package com.chen.memorizewords.domain.repository.practice

import com.chen.memorizewords.domain.model.practice.PracticeDailyDurationStats
import kotlinx.coroutines.flow.Flow

interface PracticeRecordRepository {
    suspend fun addPracticeDuration(durationMs: Long)
    fun getTodayPracticeDurationMs(): Flow<Long>
    fun getPracticeTotalDurationMs(): Flow<Long>
    fun getContinuousPracticeDays(): Flow<Int>
    fun getRecentPracticeDurationStats(dayCount: Int): Flow<List<PracticeDailyDurationStats>>
}
