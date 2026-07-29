package com.chen.memorizewords.domain.study.usecase.word.study
import com.chen.memorizewords.domain.study.model.record.CalendarDayStats
import com.chen.memorizewords.domain.study.service.StudyStatsFacade
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetMonthCalendarStatsUseCase @Inject constructor(
    private val studyStatsFacade: StudyStatsFacade
) {
    operator fun invoke(startDate: String, endDate: String): Flow<List<CalendarDayStats>> {
        return studyStatsFacade.getCalendarDayStats(startDate, endDate)
    }
}
