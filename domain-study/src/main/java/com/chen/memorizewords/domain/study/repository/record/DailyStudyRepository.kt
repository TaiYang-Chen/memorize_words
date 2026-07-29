package com.chen.memorizewords.domain.study.repository.record

import com.chen.memorizewords.domain.study.model.record.CalendarDayStats
import com.chen.memorizewords.domain.study.model.record.CheckInRecord
import com.chen.memorizewords.domain.study.model.record.DayCheckInDetail
import com.chen.memorizewords.domain.study.model.record.DailyDurationStats
import com.chen.memorizewords.domain.study.model.record.DailyStudySummary
import kotlinx.coroutines.flow.Flow

interface BusinessDateProvider {
    fun currentBusinessDate(): String
}

interface DailyStudyRepository {
    suspend fun addStudyDuration(durationMs: Long)
    fun getContinuousCheckInDays(): Flow<Int>
    fun getStudyDuration(date: String): Flow<Long>
    fun getStudyTotalDurationMs(): Flow<Long>
    fun getDailyDurationStats(startDate: String, endDate: String): Flow<List<DailyDurationStats>>
    fun getDailyCalendarStats(startDate: String, endDate: String): Flow<List<CalendarDayStats>>
    fun getDailyStudySummary(date: String): Flow<DailyStudySummary>
    fun getDayCheckInDetail(date: String): Flow<DayCheckInDetail>
    fun observeCheckInRecord(date: String): Flow<CheckInRecord?>
    suspend fun makeUpCheckIn(date: String): Result<CheckInRecord>
}
