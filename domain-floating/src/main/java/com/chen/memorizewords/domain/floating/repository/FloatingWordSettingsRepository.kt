package com.chen.memorizewords.domain.floating.repository
import com.chen.memorizewords.domain.floating.model.FloatingDockState
import com.chen.memorizewords.domain.floating.model.FloatingWordSettings
import kotlinx.coroutines.flow.Flow

interface FloatingWordSettingsRepository {
    fun observeSettings(): Flow<FloatingWordSettings>
    suspend fun getSettings(): FloatingWordSettings
    suspend fun saveSettings(settings: FloatingWordSettings)
    suspend fun updateSettings(
        transform: (FloatingWordSettings) -> FloatingWordSettings
    ): FloatingWordSettings {
        val current = getSettings()
        val updated = transform(current)
        if (updated != current) saveSettings(updated)
        return updated
    }
    suspend fun updateBallPosition(x: Int, y: Int, dockState: FloatingDockState?)
}
