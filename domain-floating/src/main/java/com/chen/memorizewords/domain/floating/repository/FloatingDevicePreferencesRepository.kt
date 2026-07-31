package com.chen.memorizewords.domain.floating.repository

import com.chen.memorizewords.domain.floating.model.FloatingDevicePreferences
import kotlinx.coroutines.flow.Flow

interface FloatingDevicePreferencesRepository {
    fun observe(): Flow<FloatingDevicePreferences>
    suspend fun get(): FloatingDevicePreferences
    suspend fun update(transform: (FloatingDevicePreferences) -> FloatingDevicePreferences): FloatingDevicePreferences
    suspend fun clear()
}
