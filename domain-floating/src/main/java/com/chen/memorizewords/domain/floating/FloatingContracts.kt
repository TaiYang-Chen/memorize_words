package com.chen.memorizewords.domain.floating
import com.chen.memorizewords.domain.word.WordRef
import kotlinx.coroutines.flow.Flow

data class FloatingSettings(
    val enabled: Boolean,
    val sourceWordIds: List<Long>
)

interface FloatingRepository {
    fun observeSettings(): Flow<FloatingSettings>
    suspend fun saveSettings(settings: FloatingSettings)
    suspend fun nextWord(): WordRef?
}
