package com.chen.memorizewords.domain.wordbook.service
import com.chen.memorizewords.domain.wordbook.model.WordBookUpdatePrompt
import com.chen.memorizewords.domain.wordbook.model.WordBookUpdateUiState
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
