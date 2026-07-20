package com.chen.memorizewords.feature.floatingreview.ui.character

import com.chen.memorizewords.core.navigation.CharacterSelectionMode
import com.chen.memorizewords.domain.floating.model.CharacterPackCatalogItem
import com.chen.memorizewords.domain.floating.model.CharacterPackDownloadState
import com.chen.memorizewords.domain.floating.model.CharacterPackDownloadStatus

data class CharacterPackUiItem(
    val packId: String,
    val packVersion: Int,
    val displayName: String,
    val description: String?,
    val previewUrl: String?,
    val packageSizeBytes: Long,
    /** True only when the current directory has one unambiguous server default. */
    val defaultPack: Boolean,
    val selected: Boolean,
    val installed: Boolean,
    val usable: Boolean,
    val updateAvailable: Boolean,
    val accountSelectedMissing: Boolean,
    val catalogItem: CharacterPackCatalogItem?,
    val download: CharacterPackDownloadState?,
    val sortOrder: Int
)


/**
 * The default is assigned by the server for users who have never made a choice. Do not infer a
 * replacement from ordering when the directory is incomplete or invalid: doing so would make a
 * client-side guess look like a user-selected character.
 */
internal object CharacterPackCatalogPolicy {
    fun uniqueDefaultPackId(catalog: List<CharacterPackCatalogItem>): String? {
        return catalog.singleOrNull { it.isDefault }?.packId
    }
}
internal object CharacterPackReloadPolicy {
    fun selectedCompletedDownload(
        mode: CharacterSelectionMode,
        settingsEnabled: Boolean,
        items: List<CharacterPackUiItem>
    ): CompletedCharacterPackDownload? {
        if (mode != CharacterSelectionMode.MANAGE || !settingsEnabled) return null
        return items.asSequence()
            .filter { item -> item.selected && item.usable }
            .firstNotNullOfOrNull { item ->
                val download = item.download ?: return@firstNotNullOfOrNull null
                download.downloadRequestId?.takeIf { requestId ->
                    download.status == CharacterPackDownloadStatus.COMPLETED &&
                        !download.selectAfterInstall &&
                        download.activationRequestId == null
                }?.let { requestId ->
                    CompletedCharacterPackDownload(item.packId, requestId)
                }
            }
    }
}

internal data class CompletedCharacterPackDownload(
    val packId: String,
    val requestId: String
)
