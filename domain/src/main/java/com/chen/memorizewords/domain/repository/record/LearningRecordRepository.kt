package com.chen.memorizewords.domain.repository.record

import com.chen.memorizewords.domain.model.study.record.CalendarDayStats
import com.chen.memorizewords.domain.model.study.record.CheckInRecord
import com.chen.memorizewords.domain.model.study.record.DayCheckInDetail
import com.chen.memorizewords.domain.model.study.record.DailyStudySummary
import com.chen.memorizewords.domain.model.study.record.DailyStudyWordRecord
import com.chen.memorizewords.domain.model.study.record.DailyDurationStats
import com.chen.memorizewords.domain.model.study.record.DailyWordStats
import com.chen.memorizewords.domain.model.study.record.TodayCheckInEntryState
import com.chen.memorizewords.domain.model.words.word.Word
import kotlinx.coroutines.flow.Flow

interface LearningRecordRepository {
    fun getCurrentBusinessDate(): String
    suspend fun addLearningRecord(
        word: Word,
        definition: String,
        isNewWord: Boolean
    )

    suspend fun addStudyDuration(durationMs: Long)

    fun getStudyTotalDayCount(): Flow<Int>
    fun getContinuousCheckInDays(): Flow<Int>
    fun getTodayNewWordCount(): Flow<Int>
    fun getTodayReviewWordCount(): Flow<Int>
    fun getTodayStudyDurationMs(): Flow<Long>
    fun getStudyTotalDurationMs(): Flow<Long>
    fun getDailyWordStats(startDate: String, endDate: String): Flow<List<DailyWordStats>>
    fun getDailyDurationStats(startDate: String, endDate: String): Flow<List<DailyDurationStats>>
    fun getCalendarDayStats(startDate: String, endDate: String): Flow<List<CalendarDayStats>>
    fun getDailyStudyWordRecords(date: String): Flow<List<DailyStudyWordRecord>>
    fun getDailyStudySummary(date: String): Flow<DailyStudySummary>
    fun getDayCheckInDetail(date: String): Flow<DayCheckInDetail>
    suspend fun getTodayCheckInEntryState(): TodayCheckInEntryState
    suspend fun makeUpCheckIn(date: String): Result<CheckInRecord>
    suspend fun autoCheckInTodayIfEligible(): Result<CheckInRecord?>
}
