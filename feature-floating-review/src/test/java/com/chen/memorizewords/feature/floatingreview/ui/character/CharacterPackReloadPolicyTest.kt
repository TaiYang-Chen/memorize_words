package com.chen.memorizewords.feature.floatingreview.ui.character

import com.chen.memorizewords.core.navigation.CharacterSelectionMode
import com.chen.memorizewords.domain.floating.model.CharacterPackDownloadState
import com.chen.memorizewords.domain.floating.model.CharacterPackDownloadStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CharacterPackReloadPolicyTest {
    @Test
    fun `visible management page reloads a newly installed version of selected pack once`() {
        val item = selectedItem(completedRequestId = "update-request")

        assertEquals(
            CompletedCharacterPackDownload("pack-a", "update-request"),
            CharacterPackReloadPolicy.selectedCompletedDownload(
                mode = CharacterSelectionMode.MANAGE,
                settingsEnabled = true,
                items = listOf(item)
            )
        )
    }

    @Test
    fun `download completion cannot start or change an inactive or unselected pack`() {
        val selected = selectedItem(completedRequestId = "update-request")
        val unselected = selected.copy(selected = false)

        assertNull(
            CharacterPackReloadPolicy.selectedCompletedDownload(
                mode = CharacterSelectionMode.MANAGE,
                settingsEnabled = false,
                items = listOf(selected)
            )
        )
        assertNull(
            CharacterPackReloadPolicy.selectedCompletedDownload(
                mode = CharacterSelectionMode.MANAGE,
                settingsEnabled = true,
                items = listOf(unselected)
            )
        )
        assertNull(
            CharacterPackReloadPolicy.selectedCompletedDownload(
                mode = CharacterSelectionMode.ACTIVATE,
                settingsEnabled = true,
                items = listOf(selected)
            )
        )
    }

    private fun selectedItem(completedRequestId: String) = CharacterPackUiItem(
        packId = "pack-a",
        packVersion = 2,
        displayName = "绿宠",
        description = null,
        previewUrl = null,
        packageSizeBytes = 1L,
        defaultPack = true,
        selected = true,
        installed = true,
        usable = true,
        updateAvailable = false,
        accountSelectedMissing = false,
        catalogItem = null,
        download = CharacterPackDownloadState(
            packId = "pack-a",
            packVersion = 2,
            downloadRequestId = completedRequestId,
            status = CharacterPackDownloadStatus.COMPLETED,
            selectAfterInstall = false,
            activationRequestId = null
        ),
        sortOrder = 0
    )
}
