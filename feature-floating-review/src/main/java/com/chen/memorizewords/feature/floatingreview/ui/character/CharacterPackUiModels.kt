package com.chen.memorizewords.feature.floatingreview.ui.character

import com.chen.memorizewords.domain.floating.model.CharacterPackCatalogItem
import com.chen.memorizewords.domain.floating.model.CharacterPackDownloadState

data class CharacterPackUiItem(
    val packId: String,
    val packVersion: Int,
    val displayName: String,
    val description: String?,
    val previewUrl: String?,
    val packageSizeBytes: Long,
    val builtIn: Boolean,
    val selected: Boolean,
    val installed: Boolean,
    val updateAvailable: Boolean,
    val accountSelectedMissing: Boolean,
    val catalogItem: CharacterPackCatalogItem?,
    val download: CharacterPackDownloadState?
)
