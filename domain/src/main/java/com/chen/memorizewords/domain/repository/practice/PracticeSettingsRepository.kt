package com.chen.memorizewords.domain.repository.practice

import com.chen.memorizewords.domain.model.practice.PracticeSettings
import kotlinx.coroutines.flow.Flow

interface PracticeSettingsRepository {
    fun observeSettings(): Flow<PracticeSettings>
    suspend fun getSettings(): PracticeSettings
    suspend fun saveSettings(settings: PracticeSettings)
}
