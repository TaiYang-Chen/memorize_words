package com.chen.memorizewords.domain.repository.floating

import com.chen.memorizewords.domain.model.floating.FloatingDockState
import com.chen.memorizewords.domain.model.floating.FloatingWordSettings
import kotlinx.coroutines.flow.Flow

interface FloatingWordSettingsRepository {
    fun observeSettings(): Flow<FloatingWordSettings>
    suspend fun getSettings(): FloatingWordSettings
    suspend fun saveSettings(settings: FloatingWordSettings)
    suspend fun updateBallPosition(x: Int, y: Int, dockState: FloatingDockState?)
}
