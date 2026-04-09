package com.chen.memorizewords.domain.service.wordbook

import com.chen.memorizewords.domain.model.wordbook.WordBookUpdatePrompt
import com.chen.memorizewords.domain.model.wordbook.WordBookUpdateUiState
import kotlinx.coroutines.flow.Flow

interface WordBookUpdateCoordinator {
    fun observeUiState(): Flow<WordBookUpdateUiState>
    fun observeForegroundPrompt(): Flow<WordBookUpdatePrompt?>
    fun observeLocalNotificationPrompts(): Flow<WordBookUpdatePrompt>
    suspend fun onAppForeground(deliverAsNotification: Boolean = false)
    suspend fun onWordBookPageEntered()
    suspend fun openUpdatePageFromPrompt()
    suspend fun remindLater()
    suspend fun ignoreVersion()
    suspend fun confirmUpdate()
    suspend fun updateForegroundAlertsEnabled(enabled: Boolean)
    suspend fun updateSilentUpdateEnabled(enabled: Boolean)
    suspend fun dismissDetails()
    suspend fun showDetails()
    suspend fun dismissSettings()
    suspend fun showSettings()
}
