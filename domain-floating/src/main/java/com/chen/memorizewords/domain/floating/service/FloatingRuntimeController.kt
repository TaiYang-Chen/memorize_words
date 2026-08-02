package com.chen.memorizewords.domain.floating.service

import com.chen.memorizewords.domain.floating.model.CharacterPackResolution
import com.chen.memorizewords.domain.floating.model.CharacterPackDownloadStatus
import com.chen.memorizewords.domain.floating.model.FloatingRuntimeEligibility
import com.chen.memorizewords.domain.floating.model.FloatingRuntimeError
import com.chen.memorizewords.domain.floating.model.FloatingRuntimeEvent
import com.chen.memorizewords.domain.floating.model.FloatingRuntimePhase
import com.chen.memorizewords.domain.floating.model.FloatingRuntimeSession
import com.chen.memorizewords.domain.floating.model.FloatingRuntimeSnapshot
import com.chen.memorizewords.domain.floating.model.FloatingRuntimeSource
import com.chen.memorizewords.domain.floating.repository.CharacterPackRepository
import com.chen.memorizewords.domain.floating.repository.FloatingDevicePreferencesRepository
import com.chen.memorizewords.domain.floating.repository.FloatingRuntimeSessionRepository
import com.chen.memorizewords.domain.floating.repository.FloatingWordSettingsRepository
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Lifecycle transition owner for the floating experience. UI and character management invoke it;
 * main-process startup owns foreground recovery and the injected gateway dispatches service commands.
 */
@Singleton
class FloatingRuntimeController @Inject constructor(
    private val runtimeRepository: FloatingRuntimeSessionRepository,
    private val reporter: FloatingRuntimeReporter,
    private val devicePreferencesRepository: FloatingDevicePreferencesRepository,
    private val settingsRepository: FloatingWordSettingsRepository,
    private val characterPackRepository: CharacterPackRepository,
    private val eligibilityChecker: FloatingRuntimeEligibilityChecker,
    private val serviceGateway: FloatingRuntimeServiceGateway
) {
    private val transitionMutex = Mutex()
    private var orphanRuntimeReconciled = false
    private var lastDispatchedStart: StartDispatch? = null

    fun observeRuntime(): Flow<FloatingRuntimeSnapshot> = runtimeRepository.observe()

    suspend fun currentRuntime(): FloatingRuntimeSnapshot = runtimeRepository.getSnapshot()

    suspend fun requestStart(source: FloatingRuntimeSource): FloatingRuntimeSnapshot = transitionMutex.withLock {
        requestStartLocked(source)
    }

    private suspend fun requestStartLocked(
        source: FloatingRuntimeSource,
        requestedPackId: String? = null
    ): FloatingRuntimeSnapshot {
        val current = runtimeRepository.getSnapshot().session
        if (current != null && current.phase != FloatingRuntimePhase.FAILED) {
            return FloatingRuntimeSnapshot(current)
        }
        val now = System.currentTimeMillis()
        val session = FloatingRuntimeSession(
            sessionId = UUID.randomUUID().toString(),
            revision = 0L,
            phase = FloatingRuntimePhase.RESOLVING,
            source = source,
            createdAtMs = now,
            updatedAtMs = now
        )
        runtimeRepository.create(session)
        if (requestedPackId == null) {
            resolveAndAdvance(session)
        } else {
            resolveSelectedPack(session, requestedPackId)
        }
        return runtimeRepository.getSnapshot()
    }

    suspend fun requestStop(): FloatingRuntimeSnapshot = transitionMutex.withLock {
        requestStopLocked()
    }

    private suspend fun requestStopLocked(): FloatingRuntimeSnapshot {
        val current = runtimeRepository.getSnapshot().session ?: return FloatingRuntimeSnapshot()
        if (current.phase == FloatingRuntimePhase.FAILED) {
            runtimeRepository.clear()
            return FloatingRuntimeSnapshot()
        }
        val stopping = reporter.transition(
            current.sessionId,
            current.revision,
            FloatingRuntimeEvent.StopRequested
        ) ?: return runtimeRepository.getSnapshot()
        val didNotReachService = current.phase in setOf(
            FloatingRuntimePhase.RESOLVING,
            FloatingRuntimePhase.AWAITING_PERMISSION,
            FloatingRuntimePhase.AWAITING_CHARACTER,
            FloatingRuntimePhase.DOWNLOADING,
            FloatingRuntimePhase.INSTALLING,
            FloatingRuntimePhase.READY_TO_START
        )
        if (current.phase == FloatingRuntimePhase.DOWNLOADING || current.phase == FloatingRuntimePhase.INSTALLING) {
            current.targetPackId?.let { packId -> runCatching { characterPackRepository.cancelDownload(packId) } }
        }
        if (didNotReachService) {
            reporter.transition(
                stopping.sessionId,
                stopping.revision,
                FloatingRuntimeEvent.Stopped
            )
            return runtimeRepository.getSnapshot()
        }
        val stopResult = serviceGateway.dispatchStop(stopping)
        if (stopResult.isFailure) {
            reporter.transition(
                stopping.sessionId,
                stopping.revision,
                FloatingRuntimeEvent.Failed(FloatingRuntimeError.STOP_FAILED)
            )
        }
        return runtimeRepository.getSnapshot()
    }

    suspend fun submitPermissionResult(granted: Boolean): FloatingRuntimeSnapshot = transitionMutex.withLock {
        val current = runtimeRepository.getSnapshot().session
            ?: return@withLock FloatingRuntimeSnapshot()
        if (current.phase != FloatingRuntimePhase.AWAITING_PERMISSION) {
            return@withLock FloatingRuntimeSnapshot(current)
        }
        if (!granted) {
            reporter.transition(
                current.sessionId,
                current.revision,
                FloatingRuntimeEvent.Failed(FloatingRuntimeError.PERMISSION_DENIED)
            )
        } else {
            startServiceIfReady(current)
        }
        runtimeRepository.getSnapshot()
    }

    suspend fun changeCharacter(packId: String): FloatingRuntimeSnapshot = transitionMutex.withLock {
        val previous = runtimeRepository.getSnapshot().session
        val shouldResumeRuntime = previous != null && previous.phase != FloatingRuntimePhase.FAILED
        if (previous != null) {
            requestStopLocked()
            // A replacement invalidates every callback from the old download or renderer.
            runtimeRepository.clear()
        }
        if (characterPackRepository.isInstalledUsable(packId)) {
            val applied = characterPackRepository.applyCharacterPack(packId)
            if (applied.isFailure) return@withLock runtimeRepository.getSnapshot()
            settingsRepository.updateSettings { settings -> settings.copy(selectedCharacterPackId = packId) }
        }
        if (shouldResumeRuntime) {
            requestStartLocked(FloatingRuntimeSource.CHARACTER_SELECTION, packId)
        } else {
            runtimeRepository.getSnapshot()
        }
    }

    suspend fun reconcileForeground(
        allowAutoStart: Boolean = false
    ): FloatingRuntimeSnapshot = transitionMutex.withLock {
        val snapshot = runtimeRepository.getSnapshot()
        val current = snapshot.session
        if (current == null) {
            if (!orphanRuntimeReconciled) {
                orphanRuntimeReconciled = serviceGateway.dispatchStop(null).isSuccess
            }
            if (allowAutoStart && devicePreferencesRepository.get().autoStartOnAppLaunch) {
                return@withLock requestStartLocked(FloatingRuntimeSource.APP_LAUNCH)
            }
            return@withLock snapshot
        }
        if (
            current.phase == FloatingRuntimePhase.FAILED &&
            allowAutoStart &&
            devicePreferencesRepository.get().autoStartOnAppLaunch
        ) {
            runtimeRepository.clear()
            return@withLock requestStartLocked(FloatingRuntimeSource.APP_LAUNCH)
        }
        when (current.phase) {
            FloatingRuntimePhase.RESOLVING -> resolveAndAdvance(current)
            FloatingRuntimePhase.AWAITING_PERMISSION -> {
                if (serviceGateway.canDrawOverlays()) startServiceIfReady(current)
            }
            FloatingRuntimePhase.DOWNLOADING -> Unit
            FloatingRuntimePhase.INSTALLING -> recoverLegacyInstallingSession(current)
            FloatingRuntimePhase.READY_TO_START -> startServiceIfReady(current)
            FloatingRuntimePhase.STARTING -> {
                val deadline = current.startDeadlineAtMs
                when {
                    deadline == null -> {
                        val restarted = reporter.transition(
                            current.sessionId,
                            current.revision,
                            FloatingRuntimeEvent.StartDispatched(
                                System.currentTimeMillis() + START_TIMEOUT_MS
                            )
                        )
                        if (restarted != null) dispatchStartIfNeeded(restarted)
                    }
                    deadline <= System.currentTimeMillis() -> {
                        reporter.transition(
                            current.sessionId,
                            current.revision,
                            FloatingRuntimeEvent.Failed(FloatingRuntimeError.RENDER_TIMEOUT)
                        )
                    }
                    else -> dispatchStartIfNeeded(current)
                }
            }
            FloatingRuntimePhase.RUNNING -> {
                val heartbeat = current.lastHeartbeatAtMs
                if (heartbeat != null && heartbeat + HEARTBEAT_STALE_MS < System.currentTimeMillis()) {
                    val stopping = reporter.transition(
                        current.sessionId,
                        current.revision,
                        FloatingRuntimeEvent.StopRequested
                    )
                    if (stopping != null) {
                        val stopResult = serviceGateway.dispatchStop(stopping)
                        if (stopResult.isFailure) {
                            reporter.transition(
                                stopping.sessionId,
                                stopping.revision,
                                FloatingRuntimeEvent.Failed(FloatingRuntimeError.STOP_FAILED)
                            )
                        }
                    }
                } else if (current.configVersion > 0L) {
                    // Re-send an idempotent configuration command after a foreground return.
                    // This recovers a dispatch that raced a remote-service heartbeat.
                    serviceGateway.dispatchReconfigure(current)
                }
            }
            FloatingRuntimePhase.STOPPING -> {
                if (current.updatedAtMs + START_TIMEOUT_MS < System.currentTimeMillis()) {
                    reporter.transition(
                        current.sessionId,
                        current.revision,
                        FloatingRuntimeEvent.Failed(FloatingRuntimeError.STOP_FAILED)
                    )
                }
            }
            else -> Unit
        }
        runtimeRepository.getSnapshot()
    }

    suspend fun requestReconfigure(): FloatingRuntimeSnapshot = transitionMutex.withLock {
        val current = runtimeRepository.getSnapshot().session
            ?: return@withLock FloatingRuntimeSnapshot()
        if (current.phase != FloatingRuntimePhase.RUNNING) return@withLock FloatingRuntimeSnapshot(current)
        val reconfigured = reporter.transition(
            current.sessionId,
            current.revision,
            FloatingRuntimeEvent.Reconfigured(current.configVersion + 1)
        ) ?: return@withLock runtimeRepository.getSnapshot()
        serviceGateway.dispatchReconfigure(reconfigured)
        FloatingRuntimeSnapshot(reconfigured)
    }

    private suspend fun resolveAndAdvance(session: FloatingRuntimeSession) {
        if (session.phase != FloatingRuntimePhase.RESOLVING) return
        val eligibility = eligibilityChecker.checkEligibility()
        if (eligibility != FloatingRuntimeEligibility.ELIGIBLE) {
            reporter.transition(
                session.sessionId,
                session.revision,
                FloatingRuntimeEvent.Failed(FloatingRuntimeError.MEMBERSHIP_REQUIRED)
            )
            return
        }
        val resolution = characterPackRepository.resolveAppliedCharacterPack()
        resolution.fold(
            onSuccess = { value ->
                when (value) {
                    is CharacterPackResolution.Resolved -> {
                        settingsRepository.updateSettings { settings ->
                            settings.copy(selectedCharacterPackId = value.item.packId)
                        }
                        val resolved = reporter.transition(
                            session.sessionId,
                            session.revision,
                            FloatingRuntimeEvent.Resolved(value.item.packId)
                        ) ?: return@fold
                        if (characterPackRepository.isInstalledUsable(value.item.packId)) {
                            startServiceIfReady(resolved)
                        } else {
                            queueDownload(resolved, value.item)
                        }
                    }
                    CharacterPackResolution.SelectionRequired -> {
                        reporter.transition(
                            session.sessionId,
                            session.revision,
                            FloatingRuntimeEvent.CharacterRequired
                        )
                    }
                }
            },
            onFailure = {
                val settings = settingsRepository.getSettings()
                val cachedPackId = settings.selectedCharacterPackId
                if (cachedPackId != null && characterPackRepository.isInstalledUsable(cachedPackId)) {
                    val resolved = reporter.transition(
                        session.sessionId,
                        session.revision,
                        FloatingRuntimeEvent.Resolved(cachedPackId)
                    )
                    if (resolved != null) startServiceIfReady(resolved)
                } else {
                    reporter.transition(
                        session.sessionId,
                        session.revision,
                        FloatingRuntimeEvent.Failed(FloatingRuntimeError.CHARACTER_UNAVAILABLE)
                    )
                }
            }
        )
    }

    private suspend fun startServiceIfReady(session: FloatingRuntimeSession) {
        val packId = session.targetPackId
        if (packId == null || !characterPackRepository.isInstalledUsable(packId)) {
            reporter.transition(
                session.sessionId,
                session.revision,
                FloatingRuntimeEvent.Failed(FloatingRuntimeError.CHARACTER_UNAVAILABLE)
            )
            return
        }
        if (!serviceGateway.canDrawOverlays()) {
            reporter.transition(
                session.sessionId,
                session.revision,
                FloatingRuntimeEvent.PermissionRequired
            )
            return
        }
        val starting = reporter.transition(
            session.sessionId,
            session.revision,
            FloatingRuntimeEvent.StartDispatched(System.currentTimeMillis() + START_TIMEOUT_MS)
        ) ?: return
        dispatchStartIfNeeded(starting)
    }

    private suspend fun recoverLegacyInstallingSession(session: FloatingRuntimeSession) {
        val packId = session.targetPackId
        if (packId == null) {
            reporter.transition(
                session.sessionId,
                session.revision,
                FloatingRuntimeEvent.Failed(FloatingRuntimeError.CHARACTER_UNAVAILABLE)
            )
            return
        }
        val download = characterPackRepository.observeDownloads().first()[packId]
        val ownsDownload = download?.runtimeSessionId == session.sessionId
        when {
            ownsDownload && download?.status == CharacterPackDownloadStatus.COMPLETED -> {
                advanceCommittedInstallation(session, packId)
            }
            ownsDownload && download?.status in setOf(
                CharacterPackDownloadStatus.FAILED,
                CharacterPackDownloadStatus.CANCELLED
            ) -> {
                reporter.transition(
                    session.sessionId,
                    session.revision,
                    FloatingRuntimeEvent.Failed(FloatingRuntimeError.DOWNLOAD_FAILED)
                )
            }
            ownsDownload -> Unit
            characterPackRepository.isInstalledUsable(packId) -> {
                // Older releases did not always persist the runtime download owner. A usable
                // target package is sufficient to recover that durable session safely.
                advanceCommittedInstallation(session, packId)
            }
            download?.status == CharacterPackDownloadStatus.COMPLETED -> {
                reporter.transition(
                    session.sessionId,
                    session.revision,
                    FloatingRuntimeEvent.Failed(FloatingRuntimeError.CHARACTER_UNAVAILABLE)
                )
            }
            download?.status in setOf(
                CharacterPackDownloadStatus.FAILED,
                CharacterPackDownloadStatus.CANCELLED
            ) -> {
                reporter.transition(
                    session.sessionId,
                    session.revision,
                    FloatingRuntimeEvent.Failed(FloatingRuntimeError.DOWNLOAD_FAILED)
                )
            }
            else -> {
                reporter.transition(
                    session.sessionId,
                    session.revision,
                    FloatingRuntimeEvent.Failed(FloatingRuntimeError.CHARACTER_UNAVAILABLE)
                )
            }
        }
    }

    private suspend fun advanceCommittedInstallation(
        session: FloatingRuntimeSession,
        packId: String
    ) {
        if (!characterPackRepository.isInstalledUsable(packId)) {
            reporter.transition(
                session.sessionId,
                session.revision,
                FloatingRuntimeEvent.Failed(FloatingRuntimeError.CHARACTER_UNAVAILABLE)
            )
            return
        }
        val ready = reporter.transition(
            session.sessionId,
            session.revision,
            FloatingRuntimeEvent.InstallationReady
        ) ?: return
        startServiceIfReady(ready)
    }

    private suspend fun dispatchStartIfNeeded(session: FloatingRuntimeSession) {
        val dispatch = StartDispatch(session.sessionId, session.revision)
        if (lastDispatchedStart == dispatch) return
        lastDispatchedStart = dispatch
        val startResult = serviceGateway.dispatchStart(session)
        if (startResult.isFailure) {
            reporter.transition(
                session.sessionId,
                session.revision,
                FloatingRuntimeEvent.Failed(FloatingRuntimeError.FOREGROUND_SERVICE_REJECTED)
            )
        }
    }

    private suspend fun resolveSelectedPack(
        session: FloatingRuntimeSession,
        packId: String
    ) {
        val resolved = reporter.transition(
            session.sessionId,
            session.revision,
            FloatingRuntimeEvent.Resolved(packId)
        ) ?: return
        if (characterPackRepository.isInstalledUsable(packId)) {
            startServiceIfReady(resolved)
            return
        }
        val catalogItem = characterPackRepository.observeCatalog().first()
            .firstOrNull { it.packId == packId }
        if (catalogItem == null) {
            reporter.transition(
                resolved.sessionId,
                resolved.revision,
                FloatingRuntimeEvent.Failed(FloatingRuntimeError.CHARACTER_UNAVAILABLE)
            )
            return
        }
        queueDownload(resolved, catalogItem)
    }

    private suspend fun queueDownload(
        session: FloatingRuntimeSession,
        item: com.chen.memorizewords.domain.floating.model.CharacterPackCatalogItem
    ) {
        val queued = reporter.transition(
            session.sessionId,
            session.revision,
            FloatingRuntimeEvent.DownloadQueued()
        ) ?: return
        val downloadResult = characterPackRepository.startDownload(
            item = item,
            selectAfterInstall = true,
            runtimeSessionId = queued.sessionId,
            runtimeRevision = queued.revision
        )
        if (downloadResult.isFailure) {
            reporter.transition(
                queued.sessionId,
                queued.revision,
                FloatingRuntimeEvent.Failed(FloatingRuntimeError.DOWNLOAD_FAILED)
            )
        }
    }

    private data class StartDispatch(
        val sessionId: String,
        val revision: Long
    )

    private companion object {
        const val START_TIMEOUT_MS = 20_000L
        const val HEARTBEAT_STALE_MS = 90_000L
    }
}
