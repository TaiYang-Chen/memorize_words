package com.chen.memorizewords.domain.study.repository.record
import com.chen.memorizewords.domain.study.model.record.CalendarDayStats
import com.chen.memorizewords.domain.study.model.record.CheckInRecord
import com.chen.memorizewords.domain.study.model.record.DayCheckInDetail
import com.chen.memorizewords.domain.study.model.record.DailyStudySummary
import com.chen.memorizewords.domain.study.model.record.DailyStudyWordRecord
import com.chen.memorizewords.domain.study.model.record.DailyDurationStats
import com.chen.memorizewords.domain.study.model.record.DailyWordStats
import com.chen.memorizewords.domain.study.model.record.TodayCheckInEntryState
import com.chen.memorizewords.domain.word.model.word.Word
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
