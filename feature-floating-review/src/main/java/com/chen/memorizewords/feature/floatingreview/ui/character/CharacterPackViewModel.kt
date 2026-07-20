package com.chen.memorizewords.feature.floatingreview.ui.character

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.chen.memorizewords.core.common.resource.ResourceProvider
import com.chen.memorizewords.core.navigation.CharacterSelectionMode
import com.chen.memorizewords.core.navigation.FloatingWordEntryExtras
import com.chen.memorizewords.core.ui.vm.BaseViewModel
import com.chen.memorizewords.domain.floating.model.CharacterPackDownloadStatus
import com.chen.memorizewords.domain.floating.model.FloatingActivationContinuation
import com.chen.memorizewords.domain.floating.model.FloatingActivationPhase
import com.chen.memorizewords.domain.floating.model.FloatingActivationSource
import com.chen.memorizewords.domain.floating.model.FloatingWordSettings
import com.chen.memorizewords.domain.floating.repository.CharacterPackRepository
import com.chen.memorizewords.domain.floating.service.FloatingActivationCoordinator
import com.chen.memorizewords.domain.floating.service.FloatingActivationIneligibleException
import com.chen.memorizewords.domain.floating.service.FloatingReviewFacade
import com.chen.memorizewords.feature.floatingreview.R
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CharacterPackViewModel @Inject constructor(
    private val repository: CharacterPackRepository,
    private val floatingReviewFacade: FloatingReviewFacade,
    private val activationCoordinator: FloatingActivationCoordinator,
    private val resources: ResourceProvider,
    private val savedStateHandle: SavedStateHandle
) : BaseViewModel() {
    sealed interface Route {
        data object ApplyCharacterPack : Route
        data object RequestOverlayPermission : Route
        data class StartFloating(val activationRequestId: String) : Route
        data object StopFloating : Route
        data object Exit : Route
        data class ConfirmDelete(val packId: String) : Route
    }

    val mode: CharacterSelectionMode = runCatching {
        CharacterSelectionMode.valueOf(
            savedStateHandle.get<String>(FloatingWordEntryExtras.EXTRA_CHARACTER_MODE).orEmpty()
        )
    }.getOrDefault(CharacterSelectionMode.MANAGE)

    private val activationRequestId = savedStateHandle.getStateFlow<String?>(
        FloatingWordEntryExtras.EXTRA_ACTIVATION_REQUEST_ID,
        savedStateHandle[FloatingWordEntryExtras.EXTRA_ACTIVATION_REQUEST_ID]
    )

    private val activationSnapshot = activationCoordinator.observeSnapshot()
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            com.chen.memorizewords.domain.floating.model.FloatingActivationSnapshot()
        )

    val readyActivationRequestId: StateFlow<String?> = combine(
        activationSnapshot,
        activationRequestId
    ) { snapshot, boundRequestId ->
        val pending = snapshot.pending
        pending?.requestId?.takeIf {
            mode == CharacterSelectionMode.ACTIVATE &&
                it == boundRequestId &&
                pending.source == FloatingActivationSource.CHARACTER_SELECTION &&
                snapshot.phase == FloatingActivationPhase.READY
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val settings = floatingReviewFacade.observeSettings()
        .stateIn(viewModelScope, SharingStarted.Eagerly, FloatingWordSettings())

    private val characterItemsWithoutDownload = combine(
        repository.observeCatalog(),
        repository.observeInstalled(),
        settings
    ) { catalog, installed, currentSettings ->
        val byId = catalog.associateBy { it.packId }
        val defaultPackId = CharacterPackCatalogPolicy.uniqueDefaultPackId(catalog)
        val selectedPackId = currentSettings.selectedCharacterPackId
        val allIds = buildSet<String> {
            addAll(byId.keys)
            addAll(installed.keys)
            selectedPackId?.let(::add)
        }
        allIds.map { id ->
            val remote = byId[id]
            val local = installed[id]
            val selected = currentSettings.selectedCharacterPackId == id
            CharacterPackUiItem(
                packId = id,
                packVersion = remote?.packVersion ?: local?.packVersion ?: 0,
                displayName = remote?.displayName ?: local?.displayName ?: id,
                description = remote?.description ?: local?.description,
                previewUrl = remote?.previewUrl ?: local?.previewUrl,
                packageSizeBytes = remote?.packageSizeBytes ?: 0L,
                defaultPack = id == defaultPackId,
                selected = selected,
                installed = local != null,
                usable = false,
                updateAvailable = remote != null && local != null && remote.packVersion > local.packVersion,
                accountSelectedMissing = selected && local == null,
                catalogItem = remote,
                download = null,
                sortOrder = remote?.sortOrder ?: Int.MAX_VALUE
            )
        }
    }.mapLatest { mappedItems ->
        mappedItems.map { item ->
            val usable = item.installed && repository.isInstalledUsable(item.packId)
            item.copy(
                usable = usable,
                accountSelectedMissing = item.selected && !usable
            )
        }.sortedWith(
            compareByDescending<CharacterPackUiItem> { it.defaultPack }
                .thenByDescending { it.usable }
                .thenByDescending { it.installed }
                .thenBy { it.sortOrder }
                .thenBy { it.packId }
        )
    }

    val items: StateFlow<List<CharacterPackUiItem>> = combine(
        characterItemsWithoutDownload,
        repository.observeDownloads()
    ) { characterItems, downloads ->
        characterItems.map { item -> item.copy(download = downloads[item.packId]) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val continuationRequestsInFlight = mutableSetOf<String>()
    private val claimedPackReloadRequestIds = linkedSetOf<String>()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            repository.refreshCatalog().onFailure {
                showToast(resources.getString(R.string.module_floating_review_character_refresh_failed))
            }
        }
    }

    internal fun claimSelectedPackReloadRequest(
        items: List<CharacterPackUiItem>
    ): CompletedCharacterPackDownload? {
        val completed = CharacterPackReloadPolicy.selectedCompletedDownload(
            mode = mode,
            settingsEnabled = settings.value.enabled,
            items = items
        ) ?: return null
        if (!claimedPackReloadRequestIds.add(completed.requestId)) return null
        while (claimedPackReloadRequestIds.size > MAX_CLAIMED_PACK_RELOAD_REQUESTS) {
            claimedPackReloadRequestIds.remove(claimedPackReloadRequestIds.first())
        }
        return completed
    }

    internal fun releasePackReloadRequestClaim(completed: CompletedCharacterPackDownload) {
        claimedPackReloadRequestIds.remove(completed.requestId)
    }

    fun onPrimary(item: CharacterPackUiItem) {
        val status = item.download?.status
        if (
            status == CharacterPackDownloadStatus.DOWNLOADING ||
            status == CharacterPackDownloadStatus.INSTALLING ||
            status == CharacterPackDownloadStatus.QUEUED
        ) return

        viewModelScope.launch {
            if (mode == CharacterSelectionMode.ACTIVATE) {
                onActivationPrimary(item)
            } else {
                onManagementPrimary(item)
            }
        }
    }

    /**
     * A selection always belongs to the account before it belongs to this device. The local
     * setting is only a cache, so it must not be changed until the server accepts the choice.
     */
    private suspend fun onActivationPrimary(item: CharacterPackUiItem) {
        if (!applyCharacterPackAndCache(item.packId, notifyRunningService = false)) return

        if (item.usable) {
            activationCoordinator.prepareInstalledPack(
                item.packId,
                FloatingActivationSource.CHARACTER_SELECTION
            ).onSuccess(::bindActivationRequest).onFailure { error ->
                handleActivationFailure(error, characterUnavailable = true)
            }
            return
        }

        val remote = item.catalogItem
        if (remote == null) {
            showCharacterUnavailable(item)
            return
        }
        activationCoordinator.startActivationDownload(
            remote,
            FloatingActivationSource.CHARACTER_SELECTION
        ).onSuccess(::bindActivationRequest).onFailure(::handleActivationFailure)
    }

    private suspend fun onManagementPrimary(item: CharacterPackUiItem) {
        val remote = item.catalogItem
        if (!item.usable || item.updateAvailable) {
            if (remote == null) {
                showCharacterUnavailable(item)
                return
            }
            val result = repository.startDownload(
                item = remote,
                // Managing a local download must not select it from a background worker.
                selectAfterInstall = false
            )
            if (result.isFailure) {
                showToast(resources.getString(R.string.module_floating_review_character_download_failed))
            }
            return
        }
        applyCharacterPackAndCache(item.packId, notifyRunningService = true)
    }

    private suspend fun applyCharacterPackAndCache(
        packId: String,
        notifyRunningService: Boolean
    ): Boolean {
        return try {
            repository.applyCharacterPack(packId).getOrThrow()
            saveSelection(packId, notifyRunningService)
            true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            showToast(resources.getString(R.string.module_floating_review_character_operation_failed))
            false
        }
    }

    private fun showCharacterUnavailable(item: CharacterPackUiItem) {
        showToast(
            resources.getString(
                if (item.installed) {
                    R.string.module_floating_review_character_damaged
                } else {
                    R.string.module_floating_review_character_missing_preview
                }
            )
        )
    }

    fun continueActivation(
        canDrawOverlays: Boolean,
        requestId: String? = activationRequestId.value,
        forcePermissionResult: Boolean = false
    ) {
        if (
            mode != CharacterSelectionMode.ACTIVATE ||
            requestId == null ||
            !continuationRequestsInFlight.add(requestId)
        ) return
        if (
            !canDrawOverlays &&
            !forcePermissionResult &&
            savedStateHandle.get<String>(KEY_PERMISSION_REQUEST_ID) == requestId
        ) {
            continuationRequestsInFlight.remove(requestId)
            return
        }
        viewModelScope.launch {
            try {
                when (
                    activationCoordinator.continueActivation(
                        canDrawOverlays = canDrawOverlays,
                        expectedRequestId = requestId
                    )
                ) {
                    FloatingActivationContinuation.ACTIVATED -> {
                        savedStateHandle[KEY_PERMISSION_REQUEST_ID] = null
                        navigateRoute(Route.StartFloating(requestId))
                    }
                    FloatingActivationContinuation.REQUIRES_PERMISSION -> {
                        savedStateHandle[KEY_PERMISSION_REQUEST_ID] = requestId
                        navigateRoute(Route.RequestOverlayPermission)
                    }
                    FloatingActivationContinuation.INELIGIBLE -> {
                        clearBoundRequest(requestId)
                        showToast(resources.getString(R.string.module_floating_review_membership_required))
                        navigateRoute(Route.Exit)
                    }
                    FloatingActivationContinuation.NO_PENDING_REQUEST,
                    FloatingActivationContinuation.STALE_REQUEST -> clearBoundRequest(requestId)
                    FloatingActivationContinuation.WAITING_FOR_CHARACTER -> Unit
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                handleActivationFailure(error)
            } finally {
                continuationRequestsInFlight.remove(requestId)
            }
        }
    }

    fun onOverlayPermissionResult(granted: Boolean) {
        val requestId = savedStateHandle.get<String>(KEY_PERMISSION_REQUEST_ID) ?: return
        if (granted) {
            continueActivation(
                canDrawOverlays = true,
                requestId = requestId,
                forcePermissionResult = true
            )
        } else {
            onOverlayPermissionDenied(requestId)
        }
    }

    fun onOverlayPermissionDenied(
        requestId: String? = savedStateHandle[KEY_PERMISSION_REQUEST_ID]
            ?: activationRequestId.value
    ) {
        requestId ?: return
        viewModelScope.launch {
            try {
                val denied = activationCoordinator.denyOverlayPermission(requestId)
                clearBoundRequest(requestId)
                if (denied) {
                    navigateRoute(Route.StopFloating)
                    showToast(resources.getString(R.string.module_floating_review_permission_denied))
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                handleActivationFailure(error)
            }
        }
    }

    fun onForegroundServiceStartRejected(requestId: String) {
        if (mode != CharacterSelectionMode.ACTIVATE) return
        viewModelScope.launch {
            try {
                if (activationCoordinator.rejectForegroundServiceStart(requestId)) {
                    clearBoundRequest(requestId)
                    navigateRoute(Route.StopFloating)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // The fragment presents the immediate failure message. Avoid a duplicate toast.
            }
        }
    }

    fun onCancel(item: CharacterPackUiItem) {
        val cancelledRequestId = item.download?.activationRequestId
        val boundRequestId = activationRequestId.value
        viewModelScope.launch {
            try {
                if (mode == CharacterSelectionMode.ACTIVATE) {
                    if (cancelledRequestId == null || cancelledRequestId != boundRequestId) {
                        return@launch
                    }
                    if (activationCoordinator.cancelPendingDownload(cancelledRequestId)) {
                        clearBoundRequest(cancelledRequestId)
                        navigateRoute(Route.StopFloating)
                    }
                } else if (cancelledRequestId == null) {
                    repository.cancelDownload(item.packId)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                showToast(
                    resources.getString(
                        R.string.module_floating_review_character_operation_failed
                    )
                )
            }
        }
    }

    fun cancelActivationAndExit() {
        if (mode != CharacterSelectionMode.ACTIVATE) {
            navigateRoute(Route.Exit)
            return
        }
        val requestId = activationRequestId.value
        viewModelScope.launch {
            try {
                val snapshot = activationSnapshot.value
                val activeDownload = snapshot.download
                val ownsActiveDownload = activeDownload?.let {
                    it.activationRequestId == requestId && it.status.isActiveDownload()
                } == true
                val cancelled = if (
                    requestId != null &&
                    snapshot.pending?.requestId == requestId &&
                    ownsActiveDownload
                ) {
                    activationCoordinator.cancelPendingDownload(requestId)
                } else if (requestId != null) {
                    activationCoordinator.cancelPending(requestId)
                } else {
                    false
                }
                clearBoundRequest(requestId)
                if (cancelled) navigateRoute(Route.StopFloating)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                showToast(resources.getString(R.string.module_floating_review_character_operation_failed))
            }
            navigateRoute(Route.Exit)
        }
    }

    fun onDelete(item: CharacterPackUiItem) {
        if (item.download?.status.isActiveDownload()) return
        savedStateHandle[KEY_PENDING_DELETE_PACK_ID] = item.packId
        navigateRoute(Route.ConfirmDelete(item.packId))
    }

    fun confirmPendingDelete() {
        val packId = savedStateHandle.get<String>(KEY_PENDING_DELETE_PACK_ID) ?: return
        viewModelScope.launch {
            try {
                deletePack(packId)
            } finally {
                savedStateHandle[KEY_PENDING_DELETE_PACK_ID] = null
            }
        }
    }

    fun cancelPendingDelete() {
        savedStateHandle[KEY_PENDING_DELETE_PACK_ID] = null
    }

    private suspend fun deletePack(packId: String) {
        try {
            CharacterPackDeletionExecutor(
                getSettings = floatingReviewFacade::getSettings,
                disableFloating = { activationCoordinator.disableFloating() },
                deleteInstalled = repository::deleteInstalled,
                stopFloating = { navigateRoute(Route.StopFloating) }
            ).execute(packId)
            showToast(resources.getString(R.string.module_floating_review_character_deleted))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            showToast(
                resources.getString(R.string.module_floating_review_character_operation_failed)
            )
        }
    }

    private suspend fun saveSelection(
        packId: String,
        notifyRunningService: Boolean = true
    ) {
        if (floatingReviewFacade.getSettings().selectedCharacterPackId == packId) return
        acknowledgeManagementDownloadCompletion(items.value.firstOrNull { it.packId == packId })
        val updated = floatingReviewFacade.updateSettings { current ->
            if (current.selectedCharacterPackId == packId) {
                current
            } else {
                current.copy(selectedCharacterPackId = packId)
            }
        }
        if (notifyRunningService && updated.enabled) navigateRoute(Route.ApplyCharacterPack)
    }

    private suspend fun acknowledgeManagementDownloadCompletion(item: CharacterPackUiItem?) {
        val download = item?.download ?: return
        val requestId = download.downloadRequestId ?: return
        if (
            download.status != CharacterPackDownloadStatus.COMPLETED ||
            download.selectAfterInstall ||
            download.activationRequestId != null
        ) return
        try {
            repository.acknowledgeManagementDownloadCompletion(item.packId, requestId)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // A stale completion marker must not block the user's explicit pack selection.
        }
    }

    private fun bindActivationRequest(pending: com.chen.memorizewords.domain.floating.model.PendingFloatingActivation) {
        savedStateHandle[FloatingWordEntryExtras.EXTRA_ACTIVATION_REQUEST_ID] = pending.requestId
        savedStateHandle[KEY_PERMISSION_REQUEST_ID] = null
    }

    private fun clearBoundRequest(requestId: String?) {
        if (requestId != null && activationRequestId.value == requestId) {
            savedStateHandle[FloatingWordEntryExtras.EXTRA_ACTIVATION_REQUEST_ID] = null
        }
        if (requestId != null && savedStateHandle.get<String>(KEY_PERMISSION_REQUEST_ID) == requestId) {
            savedStateHandle[KEY_PERMISSION_REQUEST_ID] = null
        }
    }

    private fun handleActivationFailure(
        error: Throwable,
        characterUnavailable: Boolean = false
    ) {
        if (error is FloatingActivationIneligibleException) {
            showToast(resources.getString(R.string.module_floating_review_membership_required))
            navigateRoute(Route.Exit)
            return
        }
        showToast(
            resources.getString(
                if (characterUnavailable) {
                    R.string.module_floating_review_character_damaged
                } else {
                    R.string.module_floating_review_character_download_failed
                }
            )
        )
    }

    private fun CharacterPackDownloadStatus?.isActiveDownload(): Boolean {
        return this == CharacterPackDownloadStatus.QUEUED ||
            this == CharacterPackDownloadStatus.DOWNLOADING ||
            this == CharacterPackDownloadStatus.INSTALLING
    }

    private companion object {
        const val KEY_PERMISSION_REQUEST_ID = "character_activation_permission_request_id"
        const val KEY_PENDING_DELETE_PACK_ID = "character_pending_delete_pack_id"
        const val MAX_CLAIMED_PACK_RELOAD_REQUESTS = 16
    }
}
