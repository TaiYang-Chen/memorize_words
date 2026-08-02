package com.chen.memorizewords.feature.floatingreview.ui.character

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.chen.memorizewords.core.common.resource.ResourceProvider
import com.chen.memorizewords.core.navigation.CharacterSelectionMode
import com.chen.memorizewords.core.navigation.FloatingWordEntryExtras
import com.chen.memorizewords.core.ui.vm.BaseViewModel
import com.chen.memorizewords.domain.floating.model.CharacterPackDownloadStatus
import com.chen.memorizewords.domain.floating.model.FloatingRuntimePhase
import com.chen.memorizewords.domain.floating.model.FloatingWordSettings
import com.chen.memorizewords.domain.floating.repository.CharacterPackRepository
import com.chen.memorizewords.domain.floating.service.FloatingReviewFacade
import com.chen.memorizewords.domain.floating.service.FloatingRuntimeController
import com.chen.memorizewords.feature.floatingreview.R
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
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
    private val runtimeController: FloatingRuntimeController,
    private val resources: ResourceProvider,
    private val savedStateHandle: SavedStateHandle
) : BaseViewModel() {
    sealed interface Route {
        data object Exit : Route
        data class ConfirmDelete(val packId: String) : Route
    }

    val mode: CharacterSelectionMode = runCatching {
        CharacterSelectionMode.valueOf(
            savedStateHandle.get<String>(FloatingWordEntryExtras.EXTRA_CHARACTER_MODE).orEmpty()
        )
    }.getOrDefault(CharacterSelectionMode.MANAGE)

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
        val allIds = buildSet {
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
            item.copy(usable = usable, accountSelectedMissing = item.selected && !usable)
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

    fun onPrimary(item: CharacterPackUiItem) {
        if (item.download?.status.isActiveDownload()) return
        viewModelScope.launch {
            val runtimePhase = runtimeController.currentRuntime().phase
            val runtimeOwnsSelection = runtimePhase !in setOf(
                FloatingRuntimePhase.IDLE,
                FloatingRuntimePhase.FAILED
            )
            val returnAfterSelection = runtimePhase == FloatingRuntimePhase.AWAITING_CHARACTER
            if (!item.usable || item.updateAvailable) {
                val remote = item.catalogItem
                if (remote == null) {
                    showCharacterUnavailable(item)
                    return@launch
                }
                if (runtimeOwnsSelection) {
                    runtimeController.changeCharacter(item.packId)
                    if (returnAfterSelection) navigateRoute(Route.Exit)
                } else {
                    repository.startDownload(
                        item = remote,
                        selectAfterInstall = false
                    ).onFailure {
                        showToast(resources.getString(R.string.module_floating_review_character_download_failed))
                    }
                }
                return@launch
            }
            runtimeController.changeCharacter(item.packId)
            if (returnAfterSelection) navigateRoute(Route.Exit)
        }
    }

    fun onCancel(item: CharacterPackUiItem) {
        viewModelScope.launch {
            val runtime = runtimeController.currentRuntime().session
            val runtimeOwnsDownload = runtime?.targetPackId == item.packId &&
                runtime.phase in setOf(
                    FloatingRuntimePhase.DOWNLOADING,
                    FloatingRuntimePhase.INSTALLING,
                    FloatingRuntimePhase.READY_TO_START
                )
            if (runtimeOwnsDownload) runtimeController.requestStop()
            else repository.cancelDownload(item.packId)
        }
    }

    fun cancelActivationAndExit() {
        viewModelScope.launch {
            runtimeController.requestStop()
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
                val selected = settings.value.selectedCharacterPackId == packId
                val runtime = runtimeController.currentRuntime().session
                if (selected || runtime?.targetPackId == packId) runtimeController.requestStop()
                repository.deleteInstalled(packId)
                showToast(resources.getString(R.string.module_floating_review_character_deleted))
            } catch (_: Exception) {
                showToast(resources.getString(R.string.module_floating_review_character_operation_failed))
            } finally {
                savedStateHandle[KEY_PENDING_DELETE_PACK_ID] = null
            }
        }
    }

    fun cancelPendingDelete() {
        savedStateHandle[KEY_PENDING_DELETE_PACK_ID] = null
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

    private fun CharacterPackDownloadStatus?.isActiveDownload(): Boolean =
        this == CharacterPackDownloadStatus.QUEUED ||
            this == CharacterPackDownloadStatus.DOWNLOADING ||
            this == CharacterPackDownloadStatus.INSTALLING

    private companion object {
        const val KEY_PENDING_DELETE_PACK_ID = "character_pending_delete_pack_id"
    }
}
