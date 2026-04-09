package com.chen.memorizewords.domain.service.study

import com.chen.memorizewords.domain.model.study.record.CalendarDayStats
import com.chen.memorizewords.domain.model.study.record.CheckInRecord
import com.chen.memorizewords.domain.model.study.record.DayCheckInDetail
import com.chen.memorizewords.domain.model.study.record.DailyDurationStats
import com.chen.memorizewords.domain.model.study.record.DailyStudySummary
import com.chen.memorizewords.domain.model.study.record.DailyStudyWordRecord
import com.chen.memorizewords.domain.model.study.record.DailyWordStats
import com.chen.memorizewords.domain.model.study.record.TodayCheckInEntryState
import com.chen.memorizewords.domain.repository.LearningProgressRepository
import com.chen.memorizewords.domain.repository.record.LearningRecordRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class StudyStatsFacade @Inject constructor(
    private val learningRecordRepository: LearningRecordRepository,
    private val learningProgressRepository: LearningProgressRepository
) {
    fun getCurrentBusinessDate(): String = learningRecordRepository.getCurrentBusinessDate()

    suspend fun addStudyDuration(durationMs: Long) {
        learningRecordRepository.addStudyDuration(durationMs)
    }

    fun getStudyTotalDayCount(): Flow<Int> = learningRecordRepository.getStudyTotalDayCount()

    fun getContinuousCheckInDays(): Flow<Int> = learningRecordRepository.getContinuousCheckInDays()

    fun getTodayNewWordCount(): Flow<Int> = learningRecordRepository.getTodayNewWordCount()

    fun getTodayReviewWordCount(): Flow<Int> = learningRecordRepository.getTodayReviewWordCount()

    fun getTodayStudyDurationMs(): Flow<Long> = learningRecordRepository.getTodayStudyDurationMs()

    fun getStudyTotalDurationMs(): Flow<Long> = learningRecordRepository.getStudyTotalDurationMs()

    fun getStudyTotalWordCount(): Flow<Int> = learningProgressRepository.getStudyTotalWordCount()

    fun getDailyWordStats(startDate: String, endDate: String): Flow<List<DailyWordStats>> =
        learningRecordRepository.getDailyWordStats(startDate, endDate)

    fun getDailyDurationStats(startDate: String, endDate: String): Flow<List<DailyDurationStats>> =
        learningRecordRepository.getDailyDurationStats(startDate, endDate)

    fun getCalendarDayStats(startDate: String, endDate: String): Flow<List<CalendarDayStats>> =
        learningRecordRepository.getCalendarDayStats(startDate, endDate)

    fun getDailyStudyWordRecords(date: String): Flow<List<DailyStudyWordRecord>> =
        learningRecordRepository.getDailyStudyWordRecords(date)

    fun getDailyStudySummary(date: String): Flow<DailyStudySummary> =
        learningRecordRepository.getDailyStudySummary(date)

    fun getDayCheckInDetail(date: String): Flow<DayCheckInDetail> =
        learningRecordRepository.getDayCheckInDetail(date)

    suspend fun getTodayCheckInEntryState(): TodayCheckInEntryState =
        learningRecordRepository.getTodayCheckInEntryState()

    suspend fun makeUpCheckIn(date: String): Result<CheckInRecord> =
        learningRecordRepository.makeUpCheckIn(date)

    suspend fun autoCheckInTodayIfEligible(): Result<CheckInRecord?> =
        learningRecordRepository.autoCheckInTodayIfEligible()
}
