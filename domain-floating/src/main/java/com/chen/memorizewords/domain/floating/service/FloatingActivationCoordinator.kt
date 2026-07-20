package com.chen.memorizewords.domain.floating.service

import com.chen.memorizewords.domain.floating.model.CharacterPackCatalogItem
import com.chen.memorizewords.domain.floating.model.CharacterPackDownloadStatus
import com.chen.memorizewords.domain.floating.model.CharacterPackResolution
import com.chen.memorizewords.domain.floating.model.FloatingActivationContinuation
import com.chen.memorizewords.domain.floating.model.FloatingActivationEligibility
import com.chen.memorizewords.domain.floating.model.FloatingActivationPhase
import com.chen.memorizewords.domain.floating.model.FloatingActivationPreparation
import com.chen.memorizewords.domain.floating.model.FloatingActivationSnapshot
import com.chen.memorizewords.domain.floating.model.FloatingActivationSource
import com.chen.memorizewords.domain.floating.model.PendingFloatingActivation
import com.chen.memorizewords.domain.floating.repository.CharacterPackRepository
import com.chen.memorizewords.domain.floating.repository.FloatingActivationStateRepository
import com.chen.memorizewords.domain.floating.repository.FloatingWordSettingsRepository
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class FloatingActivationIneligibleException(
    val eligibility: FloatingActivationEligibility
) : IllegalStateException("Floating activation is not eligible: ${eligibility.name}")

/**
 * Coordinates the only paths that may mark a floating word session as enabled.
 *
 * The server owns the user's applied character. A local selected id is merely a cache of that
 * decision and is never used to choose a different installed pack.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class FloatingActivationCoordinator @Inject constructor(
    private val characterPackRepository: CharacterPackRepository,
    private val settingsRepository: FloatingWordSettingsRepository,
    private val activationStateRepository: FloatingActivationStateRepository,
    private val rawEventReporter: FloatingActivationEventReporter,
    private val eligibilityChecker: FloatingActivationEligibilityChecker
) {
    private val stateMutex = Mutex()
    private val eventReporter = object : FloatingActivationEventReporter {
        override fun report(
            event: FloatingActivationEvent,
            attributes: Map<String, String>
        ) {
            try {
                rawEventReporter.report(event, attributes)
            } catch (_: Exception) {
                // Telemetry must never break activation, persistence, or cancellation semantics.
            }
        }
    }

    fun observeSnapshot(): Flow<FloatingActivationSnapshot> = combine(
        activationStateRepository.observePending(),
        characterPackRepository.observeCatalog(),
        characterPackRepository.observeInstalled(),
        characterPackRepository.observeDownloads()
    ) { pending, catalog, installed, downloads ->
        val target = pending?.targetPackId?.let { id -> catalog.firstOrNull { it.packId == id } }
        val pendingRequestId = pending?.requestId
        val download = pending?.targetPackId
            ?.let(downloads::get)
            ?.takeIf { it.activationRequestId == pendingRequestId }
        val phase = when {
            pending == null -> FloatingActivationPhase.IDLE
            download?.status == CharacterPackDownloadStatus.QUEUED -> FloatingActivationPhase.QUEUED
            download?.status == CharacterPackDownloadStatus.DOWNLOADING ->
                FloatingActivationPhase.DOWNLOADING
            download?.status == CharacterPackDownloadStatus.INSTALLING ->
                FloatingActivationPhase.INSTALLING
            download?.status == CharacterPackDownloadStatus.FAILED -> FloatingActivationPhase.FAILED
            pending.targetPackId != null && installed.containsKey(pending.targetPackId) ->
                FloatingActivationPhase.READY
            else -> FloatingActivationPhase.NEEDS_DOWNLOAD
        }
        FloatingActivationSnapshot(
            pending = pending,
            target = target,
            download = download,
            phase = phase
        )
    }.mapLatest { snapshot ->
        val packId = snapshot.pending?.targetPackId
        if (
            snapshot.phase == FloatingActivationPhase.READY &&
            packId != null &&
            !characterPackRepository.isInstalledUsable(packId)
        ) {
            snapshot.copy(phase = FloatingActivationPhase.NEEDS_DOWNLOAD)
        } else {
            snapshot
        }
    }

    /** Observes only the cached applied pack; it deliberately never falls back to another pack. */
    fun observeCurrentPackInstalled(): Flow<Boolean> = combine(
        settingsRepository.observeSettings(),
        characterPackRepository.observeInstalled()
    ) { settings, installed ->
        settings.selectedCharacterPackId?.let(installed::get)
    }.mapLatest { installed ->
        installed != null && characterPackRepository.isInstalledUsable(installed.packId)
    }

    /**
     * Resolves the server-managed applied character for every explicit activation attempt.
     *
     * When resolve is unavailable, only the same locally cached applied pack may be used offline.
     * A different installed pack is never selected as a fallback.
     */
    suspend fun prepareActivation(source: FloatingActivationSource): FloatingActivationPreparation =
        stateMutex.withLock {
            val eligibility = eligibilityChecker.checkEligibility()
            if (eligibility != FloatingActivationEligibility.ELIGIBLE) {
                rejectIneligibleLocked(
                    eligibility = eligibility,
                    pending = activationStateRepository.getPending(),
                    source = source
                )
                return@withLock FloatingActivationPreparation.INELIGIBLE
            }

            characterPackRepository.resolveAppliedCharacterPack().fold(
                onSuccess = { resolution ->
                    when (resolution) {
                        is CharacterPackResolution.Resolved ->
                            prepareResolvedCharacterLocked(resolution.item, source)

                        CharacterPackResolution.SelectionRequired ->
                            requireCharacterSelectionLocked(source)
                    }
                },
                onFailure = {
                    prepareOfflineCharacterLocked(source)
                }
            )
        }

    /**
     * Enqueues a download only for the target returned by [prepareActivation]. No default, sort
     * order, or arbitrary installed/catalog character may be used as a replacement.
     */
    suspend fun startResolvedCharacterDownload(): Result<PendingFloatingActivation> = catchAsResult {
        stateMutex.withLock {
            val eligibility = eligibilityChecker.checkEligibility()
            if (eligibility != FloatingActivationEligibility.ELIGIBLE) {
                rejectIneligibleLocked(
                    eligibility = eligibility,
                    pending = activationStateRepository.getPending(),
                    source = FloatingActivationSource.HOME
                )
                return@withLock Result.failure(FloatingActivationIneligibleException(eligibility))
            }

            val pending = activationStateRepository.getPending()
                ?: return@withLock Result.failure(
                    IllegalStateException("No resolved character activation request")
                )
            val packId = pending.targetPackId
                ?: return@withLock Result.failure(
                    IllegalStateException("Resolved character activation request has no target")
                )
            val selectedPackId = settingsRepository.getSettings().selectedCharacterPackId
            if (selectedPackId != packId) {
                return@withLock Result.failure(
                    IllegalStateException("Resolved character activation request is stale")
                )
            }
            if (characterPackRepository.isInstalledUsable(packId)) {
                return@withLock Result.success(pending)
            }

            val target = characterPackRepository.observeCatalog().first()
                .firstOrNull { it.packId == packId }
                ?: return@withLock Result.failure(
                    IllegalStateException("Resolved character is unavailable for download")
                )
            eventReporter.report(
                FloatingActivationEvent.RESOLVED_CHARACTER_DOWNLOAD_SELECTED,
                activationAttributes(pending)
            )
            enqueueActivationDownloadLocked(target, pending)
        }
    }


    /**
     * Starts an activation after the character selection screen has successfully applied [item]
     * to the server. The caller supplies the directory item it already has; this method never
     * performs another resolve or picks a substitute character.
     */
    suspend fun startActivationDownload(
        item: CharacterPackCatalogItem,
        source: FloatingActivationSource
    ): Result<PendingFloatingActivation> = catchAsResult {
        stateMutex.withLock {
            val eligibility = eligibilityChecker.checkEligibility()
            if (eligibility != FloatingActivationEligibility.ELIGIBLE) {
                rejectIneligibleLocked(
                    eligibility = eligibility,
                    pending = activationStateRepository.getPending(),
                    source = source
                )
                return@withLock Result.failure(FloatingActivationIneligibleException(eligibility))
            }
            saveSelection(item.packId)
            val pending = createPendingLocked(item.packId, source)
            val result = enqueueActivationDownloadLocked(item, pending)
            if (result.isFailure) activationStateRepository.clearPending(pending.requestId)
            result
        }
    }

    /**
     * Prepares an already installed character after the character selection screen has applied it
     * to the server. It never treats a different installed character as equivalent.
     */
    suspend fun prepareInstalledPack(
        packId: String,
        source: FloatingActivationSource
    ): Result<PendingFloatingActivation> = catchAsResult {
        stateMutex.withLock {
            val eligibility = eligibilityChecker.checkEligibility()
            if (eligibility != FloatingActivationEligibility.ELIGIBLE) {
                rejectIneligibleLocked(
                    eligibility = eligibility,
                    pending = activationStateRepository.getPending(),
                    source = source
                )
                return@withLock Result.failure(FloatingActivationIneligibleException(eligibility))
            }
            if (!characterPackRepository.isInstalledUsable(packId)) {
                return@withLock Result.failure(IllegalStateException("Character pack is not usable"))
            }
            saveSelection(packId)
            Result.success(createPendingLocked(packId, source))
        }
    }

    suspend fun continueActivation(
        canDrawOverlays: Boolean,
        expectedRequestId: String? = null
    ): FloatingActivationContinuation = stateMutex.withLock {
        val pending = activationStateRepository.getPending()
            ?: return@withLock FloatingActivationContinuation.NO_PENDING_REQUEST
        if (expectedRequestId != null && pending.requestId != expectedRequestId) {
            return@withLock FloatingActivationContinuation.STALE_REQUEST
        }
        val eligibility = eligibilityChecker.checkEligibility()
        if (eligibility != FloatingActivationEligibility.ELIGIBLE) {
            rejectIneligibleLocked(eligibility, pending = pending)
            return@withLock FloatingActivationContinuation.INELIGIBLE
        }
        val packId = pending.targetPackId
            ?: return@withLock FloatingActivationContinuation.WAITING_FOR_CHARACTER
        if (!characterPackRepository.isInstalledUsable(packId)) {
            return@withLock FloatingActivationContinuation.WAITING_FOR_CHARACTER
        }
        if (!canDrawOverlays) {
            eventReporter.report(
                FloatingActivationEvent.PERMISSION_REQUIRED,
                activationAttributes(pending)
            )
            return@withLock FloatingActivationContinuation.REQUIRES_PERMISSION
        }

        val isNewCommit = pending.committedAtMs == null
        val committedPending = if (isNewCommit) {
            eventReporter.report(
                FloatingActivationEvent.PERMISSION_GRANTED,
                activationAttributes(pending)
            )
            pending.copy(committedAtMs = System.currentTimeMillis()).also {
                activationStateRepository.savePending(it)
            }
        } else {
            pending
        }
        settingsRepository.updateSettings { settings ->
            if (
                !settings.enabled ||
                !settings.autoStartOnAppLaunch ||
                settings.selectedCharacterPackId != packId
            ) {
                settings.copy(
                    enabled = true,
                    autoStartOnAppLaunch = true,
                    selectedCharacterPackId = packId
                )
            } else {
                settings
            }
        }
        if (isNewCommit) {
            eventReporter.report(
                FloatingActivationEvent.ACTIVATION_COMMITTED,
                activationAttributes(committedPending)
            )
        }
        FloatingActivationContinuation.ACTIVATED
    }

    suspend fun completeActivationOnFloatingStarted(
        packId: String,
        expectedRequestId: String?
    ): Boolean = stateMutex.withLock {
        if (expectedRequestId.isNullOrBlank()) return@withLock false
        val pending = activationStateRepository.getPending() ?: return@withLock false
        val settings = settingsRepository.getSettings()
        if (
            pending.requestId != expectedRequestId ||
            pending.committedAtMs == null ||
            pending.targetPackId != packId ||
            !settings.enabled ||
            settings.selectedCharacterPackId != packId
        ) {
            return@withLock false
        }
        if (!activationStateRepository.clearPending(expectedRequestId)) return@withLock false
        eventReporter.report(
            FloatingActivationEvent.FLOATING_STARTED,
            activationAttributes(pending)
        )
        true
    }

    suspend fun getPendingRequestId(
        source: FloatingActivationSource? = null
    ): String? = stateMutex.withLock {
        activationStateRepository.getPending()
            ?.takeIf { source == null || it.source == source }
            ?.requestId
    }

    suspend fun denyOverlayPermission(requestId: String): Boolean = stateMutex.withLock {
        val pending = activationStateRepository.getPending() ?: return@withLock false
        if (pending.requestId != requestId) return@withLock false
        if (!activationStateRepository.clearPending(pending.requestId)) return@withLock false
        disableRunningStateLocked()
        eventReporter.report(
            FloatingActivationEvent.PERMISSION_DENIED,
            activationAttributes(pending)
        )
        true
    }

    suspend fun cancelPending(requestId: String): Boolean = stateMutex.withLock {
        val pending = activationStateRepository.getPending() ?: return@withLock false
        if (pending.requestId != requestId) return@withLock false
        if (!activationStateRepository.clearPending(pending.requestId)) return@withLock false
        disableRunningStateLocked()
        eventReporter.report(
            FloatingActivationEvent.ACTIVATION_CANCELLED,
            activationAttributes(pending)
        )
        true
    }

    /**
     * Rolls back a committed activation only when its exact foreground-service start was rejected.
     * Keeping the selected pack lets the user retry without having to download it again.
     */
    suspend fun rejectForegroundServiceStart(requestId: String): Boolean = stateMutex.withLock {
        val pending = activationStateRepository.getPending() ?: return@withLock false
        if (pending.requestId != requestId) return@withLock false
        if (!activationStateRepository.clearPending(pending.requestId)) return@withLock false
        disableRunningStateLocked()
        eventReporter.report(
            FloatingActivationEvent.ACTIVATION_CANCELLED,
            activationAttributes(
                pending,
                mapOf("reason" to "FOREGROUND_SERVICE_START_REJECTED")
            )
        )
        true
    }

    suspend fun cancelPendingDownload(requestId: String): Boolean = stateMutex.withLock {
        val pending = activationStateRepository.getPending() ?: return@withLock false
        if (pending.requestId != requestId) return@withLock false
        if (!activationStateRepository.clearPending(pending.requestId)) return@withLock false
        disableRunningStateLocked()
        pending.targetPackId?.let { packId ->
            runCatching { characterPackRepository.cancelDownload(packId) }
        }
        eventReporter.report(
            FloatingActivationEvent.ACTIVATION_CANCELLED,
            activationAttributes(pending)
        )
        true
    }

    suspend fun disableFloating() = stateMutex.withLock {
        activationStateRepository.clearPending()
        disableRunningStateLocked()
    }

    suspend fun disableRunningStatePreservingRequest() = stateMutex.withLock {
        if (activationStateRepository.getPending() == null) {
            disableRunningStateLocked()
        }
    }

    /** Disables stale enabled state but never selects another installed character. */
    suspend fun reconcileEnabledState() = stateMutex.withLock {
        val eligibility = eligibilityChecker.checkEligibility()
        if (eligibility != FloatingActivationEligibility.ELIGIBLE) {
            rejectIneligibleLocked(
                eligibility = eligibility,
                pending = activationStateRepository.getPending()
            )
            return@withLock
        }
        val settings = settingsRepository.getSettings()
        if (!settings.enabled) return@withLock
        val selectedPackId = settings.selectedCharacterPackId
        if (selectedPackId == null || !characterPackRepository.isInstalledUsable(selectedPackId)) {
            disableRunningStateLocked()
            eventReporter.report(FloatingActivationEvent.MISSING_PACK_DISABLED)
        }
    }

    suspend fun recordSetupShown(expectedRequestId: String? = null) {
        val pending = activationStateRepository.getPending()
            ?.takeIf { expectedRequestId == null || it.requestId == expectedRequestId }
        eventReporter.report(
            FloatingActivationEvent.SETUP_SHOWN,
            pending?.let { activationAttributes(it) }.orEmpty()
        )
    }

    suspend fun recordOtherCharacterSelected(expectedRequestId: String? = null) {
        val pending = activationStateRepository.getPending()
            ?.takeIf { expectedRequestId == null || it.requestId == expectedRequestId }
        eventReporter.report(
            FloatingActivationEvent.OTHER_CHARACTER_SELECTED,
            pending?.let { activationAttributes(it) }.orEmpty()
        )
    }

    /** True only when the exact cached applied character is valid locally. */
    suspend fun canStartCurrent(): Boolean {
        val settings = settingsRepository.getSettings()
        val selectedPackId = settings.selectedCharacterPackId ?: return false
        return settings.enabled && characterPackRepository.isInstalledUsable(selectedPackId)
    }

    /** True only when the exact cached applied character is valid locally. */
    suspend fun hasUsablePack(): Boolean {
        val selectedPackId = settingsRepository.getSettings().selectedCharacterPackId ?: return false
        return characterPackRepository.isInstalledUsable(selectedPackId)
    }

    /** Strict runtime check: never changes the selected character as a side effect. */
    suspend fun isCurrentPackUsable(): Boolean {
        val selectedPackId = settingsRepository.getSettings().selectedCharacterPackId ?: return false
        return characterPackRepository.isInstalledUsable(selectedPackId)
    }

    suspend fun disableIfPackMissing() {
        stateMutex.withLock {
            if (activationStateRepository.getPending() != null) return@withLock
            val selectedPackId = settingsRepository.getSettings().selectedCharacterPackId
            if (selectedPackId == null || !characterPackRepository.isInstalledUsable(selectedPackId)) {
                disableRunningStateLocked()
            }
        }
    }

    private suspend fun prepareResolvedCharacterLocked(
        item: CharacterPackCatalogItem,
        source: FloatingActivationSource
    ): FloatingActivationPreparation {
        val currentSettings = settingsRepository.getSettings()
        val targetInstalled = characterPackRepository.isInstalledUsable(item.packId)
        if (
            currentSettings.enabled &&
            currentSettings.selectedCharacterPackId != item.packId &&
            !targetInstalled
        ) {
            // A cross-device selection changed the active pet. Do not keep the old one running
            // while the server-selected replacement is being downloaded.
            disableRunningStateLocked()
        }
        saveSelection(item.packId)
        createPendingLocked(item.packId, source)
        return if (targetInstalled) {
            FloatingActivationPreparation.READY_FOR_PERMISSION
        } else {
            FloatingActivationPreparation.NEEDS_DOWNLOAD
        }
    }

    private suspend fun prepareOfflineCharacterLocked(
        source: FloatingActivationSource
    ): FloatingActivationPreparation {
        val selectedPackId = settingsRepository.getSettings().selectedCharacterPackId
        if (selectedPackId != null && characterPackRepository.isInstalledUsable(selectedPackId)) {
            createPendingLocked(selectedPackId, source, mapOf("resolution" to "OFFLINE_CACHE"))
            return FloatingActivationPreparation.READY_FOR_PERMISSION
        }
        disableRunningStateLocked()
        createPendingLocked(
            packId = null,
            source = source,
            extras = mapOf("resolution" to "UNAVAILABLE"),
            replacementReason = "RESOLVE_UNAVAILABLE"
        )
        return FloatingActivationPreparation.NO_CHARACTER_AVAILABLE
    }

    private suspend fun requireCharacterSelectionLocked(
        source: FloatingActivationSource
    ): FloatingActivationPreparation {
        clearPendingAndDisableLocked(
            reason = "SELECTION_REQUIRED",
            source = source,
            clearSelection = true
        )
        return FloatingActivationPreparation.SELECTION_REQUIRED
    }

    private suspend fun createPendingLocked(
        packId: String?,
        source: FloatingActivationSource,
        extras: Map<String, String> = emptyMap(),
        replacementReason: String = "REPLACED"
    ): PendingFloatingActivation {
        val previous = activationStateRepository.getPending()
        val pending = newPending(packId, source)
        activationStateRepository.savePending(pending)
        previous?.let {
            eventReporter.report(
                FloatingActivationEvent.ACTIVATION_CANCELLED,
                activationAttributes(it, mapOf("reason" to replacementReason))
            )
        }
        eventReporter.report(
            FloatingActivationEvent.ACTIVATION_REQUESTED,
            activationAttributes(pending, extras)
        )
        return pending
    }

    private suspend fun enqueueActivationDownloadLocked(
        item: CharacterPackCatalogItem,
        pending: PendingFloatingActivation
    ): Result<PendingFloatingActivation> = try {
        characterPackRepository.startDownload(
            item = item,
            selectAfterInstall = true,
            activationRequestId = pending.requestId
        ).getOrThrow()
        eventReporter.report(
            FloatingActivationEvent.DOWNLOAD_ENQUEUED,
            activationAttributes(pending)
        )
        Result.success(pending)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        eventReporter.report(
            FloatingActivationEvent.DOWNLOAD_FAILED,
            activationAttributes(pending, mapOf("error" to "ENQUEUE"))
        )
        Result.failure(error)
    }

    private suspend fun clearPendingAndDisableLocked(
        reason: String,
        source: FloatingActivationSource,
        clearSelection: Boolean
    ) {
        val previousPending = activationStateRepository.getPending()
        if (previousPending != null &&
            activationStateRepository.clearPending(previousPending.requestId)
        ) {
            eventReporter.report(
                FloatingActivationEvent.ACTIVATION_CANCELLED,
                activationAttributes(previousPending, mapOf("reason" to reason))
            )
        } else {
            eventReporter.report(
                FloatingActivationEvent.ACTIVATION_CANCELLED,
                mapOf("reason" to reason, "source" to source.name)
            )
        }
        if (clearSelection) clearSelection()
        disableRunningStateLocked()
    }

    private suspend fun disableRunningStateLocked() {
        settingsRepository.updateSettings { settings ->
            if (settings.enabled || settings.autoStartOnAppLaunch || settings.autoStartOnBoot) {
                settings.copy(
                    enabled = false,
                    autoStartOnBoot = false,
                    autoStartOnAppLaunch = false
                )
            } else {
                settings
            }
        }
    }

    private suspend fun rejectIneligibleLocked(
        eligibility: FloatingActivationEligibility,
        pending: PendingFloatingActivation?,
        source: FloatingActivationSource? = null
    ) {
        if (pending != null && activationStateRepository.clearPending(pending.requestId)) {
            eventReporter.report(
                FloatingActivationEvent.ACTIVATION_CANCELLED,
                activationAttributes(pending, mapOf("reason" to eligibility.name))
            )
        } else {
            eventReporter.report(
                FloatingActivationEvent.ACTIVATION_CANCELLED,
                buildMap {
                    put("reason", eligibility.name)
                    source?.let { put("source", it.name) }
                }
            )
        }
        disableRunningStateLocked()
    }

    private suspend fun saveSelection(packId: String) {
        settingsRepository.updateSettings { settings ->
            if (settings.selectedCharacterPackId != packId) {
                settings.copy(selectedCharacterPackId = packId)
            } else {
                settings
            }
        }
    }

    private suspend fun clearSelection() {
        settingsRepository.updateSettings { settings ->
            if (settings.selectedCharacterPackId != null) {
                settings.copy(selectedCharacterPackId = null)
            } else {
                settings
            }
        }
    }

    private suspend fun <T> catchAsResult(
        block: suspend () -> Result<T>
    ): Result<T> = try {
        block()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        Result.failure(error)
    }

    private fun newPending(
        packId: String?,
        source: FloatingActivationSource
    ): PendingFloatingActivation = PendingFloatingActivation(
        requestId = UUID.randomUUID().toString(),
        targetPackId = packId,
        source = source,
        createdAtMs = System.currentTimeMillis()
    )

    private fun activationAttributes(
        pending: PendingFloatingActivation,
        extras: Map<String, String> = emptyMap()
    ): Map<String, String> = buildMap {
        put("requestId", pending.requestId)
        put("source", pending.source.name)
        pending.targetPackId?.let { put("packId", it) }
        put("elapsedMs", (System.currentTimeMillis() - pending.createdAtMs).coerceAtLeast(0L).toString())
        putAll(extras)
    }
}
