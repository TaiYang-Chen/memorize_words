package com.chen.memorizewords.domain.study.service
import com.chen.memorizewords.domain.study.model.record.CalendarDayStats
import com.chen.memorizewords.domain.study.model.record.CheckInRecord
import com.chen.memorizewords.domain.study.model.record.DayCheckInDetail
import com.chen.memorizewords.domain.study.model.record.DailyDurationStats
import com.chen.memorizewords.domain.study.model.record.DailyStudySummary
import com.chen.memorizewords.domain.study.model.record.DailyStudyWordRecord
import com.chen.memorizewords.domain.study.model.record.DailyWordStats
import com.chen.memorizewords.domain.wordbook.repository.LearningProgressRepository
import com.chen.memorizewords.domain.study.repository.record.BusinessDateProvider
import com.chen.memorizewords.domain.study.repository.record.DailyStudyRepository
import com.chen.memorizewords.domain.study.repository.record.StudyRecordQuery
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class StudyStatsFacade @Inject constructor(
    private val studyRecordQuery: StudyRecordQuery,
    private val dailyStudyRepository: DailyStudyRepository,
    private val businessDateProvider: BusinessDateProvider,
    private val learningProgressRepository: LearningProgressRepository
) {
    fun getCurrentBusinessDate(): String = businessDateProvider.currentBusinessDate()

    suspend fun addStudyDuration(durationMs: Long) {
        dailyStudyRepository.addStudyDuration(durationMs)
    }

    fun getStudyTotalDayCount(): Flow<Int> = studyRecordQuery.getStudyTotalDayCount()

    fun getContinuousCheckInDays(): Flow<Int> = dailyStudyRepository.getContinuousCheckInDays()

    fun getTodayNewWordCount(): Flow<Int> =
        studyRecordQuery.getNewWordCount(businessDateProvider.currentBusinessDate())

    fun getTodayReviewWordCount(): Flow<Int> =
        studyRecordQuery.getReviewWordCount(businessDateProvider.currentBusinessDate())

    fun getTodayStudyDurationMs(): Flow<Long> =
        dailyStudyRepository.getStudyDuration(businessDateProvider.currentBusinessDate())

    fun getStudyTotalDurationMs(): Flow<Long> = dailyStudyRepository.getStudyTotalDurationMs()

    fun getStudyTotalWordCount(): Flow<Int> = learningProgressRepository.getStudyTotalWordCount()

    fun getDailyWordStats(startDate: String, endDate: String): Flow<List<DailyWordStats>> =
        studyRecordQuery.getDailyWordStats(startDate, endDate)

    fun getDailyDurationStats(startDate: String, endDate: String): Flow<List<DailyDurationStats>> =
        dailyStudyRepository.getDailyDurationStats(startDate, endDate)

    fun getCalendarDayStats(startDate: String, endDate: String): Flow<List<CalendarDayStats>> =
        combine(
            studyRecordQuery.observeStudyDatesBetween(startDate, endDate),
            dailyStudyRepository.getDailyCalendarStats(startDate, endDate)
        ) { studyDates, dailyRows ->
            val studyDateSet = studyDates.toSet()
            val dailyByDate = dailyRows.associateBy(CalendarDayStats::date)
            (studyDateSet + dailyByDate.keys).sorted().map { date ->
                dailyByDate[date]
                    ?.copy(hasStudy = date in studyDateSet || dailyByDate[date]?.hasStudy == true)
                    ?: CalendarDayStats(
                        date = date,
                        hasStudy = true,
                        hasCheckIn = false,
                        isNewPlanCompleted = false,
                        isReviewPlanCompleted = false
                    )
            }
        }

    fun getDailyStudyWordRecords(date: String): Flow<List<DailyStudyWordRecord>> =
        studyRecordQuery.getDailyStudyWordRecords(date)

    fun getDailyStudySummary(date: String): Flow<DailyStudySummary> =
        dailyStudyRepository.getDailyStudySummary(date)

    fun getDayCheckInDetail(date: String): Flow<DayCheckInDetail> =
        dailyStudyRepository.getDayCheckInDetail(date)

    fun observeCheckInRecord(date: String): Flow<CheckInRecord?> =
        dailyStudyRepository.observeCheckInRecord(date)

    suspend fun makeUpCheckIn(date: String): Result<CheckInRecord> =
        dailyStudyRepository.makeUpCheckIn(date)
}
