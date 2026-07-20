package com.chen.memorizewords.feature.floatingreview.ui.character

import com.chen.memorizewords.domain.floating.model.FloatingWordSettings

internal class CharacterPackDeletionExecutor(
    private val getSettings: suspend () -> FloatingWordSettings,
    private val disableFloating: suspend () -> Unit,
    private val deleteInstalled: suspend (String) -> Unit,
    private val stopFloating: () -> Unit
) {
    suspend fun execute(packId: String) {
        val initiallySelected = getSettings().selectedCharacterPackId == packId
        if (initiallySelected) {
            disableFloating()
            stopFloating()
        }

        deleteInstalled(packId)

        val currentSettings = getSettings()
        if (
            currentSettings.enabled &&
            currentSettings.selectedCharacterPackId == packId
        ) {
            disableFloating()
            stopFloating()
        }
    }
}