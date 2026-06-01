package com.chen.memorizewords.domain.practice
import kotlinx.coroutines.flow.Flow

interface PracticeSettingsRepository {
    fun observeSettings(): Flow<PracticeSettings>
    suspend fun getSettings(): PracticeSettings
    suspend fun saveSettings(settings: PracticeSettings)
}

interface PracticeRecordRepository {
    suspend fun addPracticeDuration(durationMs: Long)
    fun getTodayPracticeDurationMs(): Flow<Long>
    fun getPracticeTotalDurationMs(): Flow<Long>
    fun getContinuousPracticeDays(): Flow<Int>
    fun getRecentPracticeDurationStats(dayCount: Int): Flow<List<PracticeDailyDurationStats>>
}

interface PracticeSessionRecordRepository {
    suspend fun saveSessionRecord(record: PracticeSessionRecord)
    fun getRecentSessionRecords(dayCount: Int): Flow<List<PracticeSessionRecord>>
    suspend fun getSessionRecord(recordId: Long): PracticeSessionRecord?
}
