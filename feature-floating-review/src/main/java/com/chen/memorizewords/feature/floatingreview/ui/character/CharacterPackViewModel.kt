package com.chen.memorizewords.feature.floatingreview.ui.character

import androidx.lifecycle.viewModelScope
import com.chen.memorizewords.core.common.resource.ResourceProvider
import com.chen.memorizewords.core.ui.vm.BaseViewModel
import com.chen.memorizewords.domain.floating.model.CharacterPackDownloadStatus
import com.chen.memorizewords.domain.floating.model.FloatingWordSettings
import com.chen.memorizewords.domain.floating.repository.CharacterPackRepository
import com.chen.memorizewords.domain.floating.service.FloatingReviewFacade
import com.chen.memorizewords.feature.floatingreview.R
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class CharacterPackViewModel @Inject constructor(
    private val repository: CharacterPackRepository,
    private val floatingReviewFacade: FloatingReviewFacade,
    private val resources: ResourceProvider
) : BaseViewModel() {
    sealed interface Route {
        data object ApplyCharacterPack : Route
    }

    private val settings = floatingReviewFacade.observeSettings()
        .stateIn(viewModelScope, SharingStarted.Eagerly, FloatingWordSettings())

    val items: StateFlow<List<CharacterPackUiItem>> = combine(
        repository.observeCatalog(),
        repository.observeInstalled(),
        repository.observeDownloads(),
        settings
    ) { catalog, installed, downloads, currentSettings ->
        val byId = catalog.associateBy { it.packId }
        buildList {
            add(
                CharacterPackUiItem(
                    packId = FloatingWordSettings.DEFAULT_CHARACTER_PACK_ID,
                    packVersion = 1,
                    displayName = resources.getString(R.string.module_floating_review_character_default_name),
                    description = resources.getString(R.string.module_floating_review_character_default_description),
                    previewUrl = null,
                    packageSizeBytes = 0L,
                    builtIn = true,
                    selected = currentSettings.selectedCharacterPackId == FloatingWordSettings.DEFAULT_CHARACTER_PACK_ID,
                    installed = true,
                    updateAvailable = false,
                    accountSelectedMissing = false,
                    catalogItem = null,
                    download = null
                )
            )
            val allIds = (
                byId.keys + installed.keys + currentSettings.selectedCharacterPackId
            ).toSortedSet(compareBy<String> { id ->
                byId[id]?.sortOrder ?: Int.MAX_VALUE
            }.thenBy { it })
            allIds.filter { it != FloatingWordSettings.DEFAULT_CHARACTER_PACK_ID }.forEach { id ->
                val remote = byId[id]
                val local = installed[id]
                val selected = currentSettings.selectedCharacterPackId == id
                add(
                    CharacterPackUiItem(
                        packId = id,
                        packVersion = remote?.packVersion ?: local?.packVersion ?: 0,
                        displayName = remote?.displayName ?: local?.displayName ?: id,
                        description = remote?.description ?: local?.description,
                        previewUrl = remote?.previewUrl ?: local?.previewUrl,
                        packageSizeBytes = remote?.packageSizeBytes ?: 0L,
                        builtIn = false,
                        selected = selected,
                        installed = local != null,
                        updateAvailable = remote != null && local != null && remote.packVersion > local.packVersion,
                        accountSelectedMissing = selected && local == null,
                        catalogItem = remote,
                        download = downloads[id]
                    )
                )
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val appliedDownloads = mutableSetOf<String>()

    init {
        refresh()
        viewModelScope.launch {
            repository.observeDownloads().collect { downloads ->
                downloads.values
                    .filter {
                        it.status == CharacterPackDownloadStatus.COMPLETED &&
                            it.selectAfterInstall
                    }
                    .forEach { state ->
                        val key = "${state.packId}:${state.packVersion}"
                        if (appliedDownloads.add(key) && settings.value.enabled) {
                            navigateRoute(Route.ApplyCharacterPack)
                        }
                    }
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            repository.refreshCatalog().onFailure {
                showToast(resources.getString(R.string.module_floating_review_character_refresh_failed))
            }
        }
    }

    fun onPrimary(item: CharacterPackUiItem) {
        val status = item.download?.status
        if (status == CharacterPackDownloadStatus.DOWNLOADING ||
            status == CharacterPackDownloadStatus.INSTALLING ||
            status == CharacterPackDownloadStatus.QUEUED
        ) return
        val remote = item.catalogItem
        if (!item.installed || item.updateAvailable) {
            if (remote != null) {
                viewModelScope.launch {
                    repository.startDownload(
                        item = remote,
                        selectAfterInstall = !item.installed || item.selected
                    )
                }
            }
            return
        }
        select(item.packId)
    }

    fun onCancel(item: CharacterPackUiItem) {
        viewModelScope.launch { repository.cancelDownload(item.packId) }
    }

    fun onDelete(item: CharacterPackUiItem) {
        if (item.builtIn) return
        viewModelScope.launch {
            if (item.selected) {
                saveSelection(FloatingWordSettings.DEFAULT_CHARACTER_PACK_ID)
            }
            repository.deleteInstalled(item.packId)
        }
    }

    private fun select(packId: String) {
        viewModelScope.launch { saveSelection(packId) }
    }

    private suspend fun saveSelection(packId: String) {
        val current = floatingReviewFacade.getSettings()
        if (current.selectedCharacterPackId == packId) return
        floatingReviewFacade.saveSettings(current.copy(selectedCharacterPackId = packId))
        if (current.enabled) navigateRoute(Route.ApplyCharacterPack)
    }
}
