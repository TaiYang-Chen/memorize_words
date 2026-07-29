package com.chen.memorizewords.domain.study.usecase.word.study
import com.chen.memorizewords.domain.study.model.record.DailyWordStats
import com.chen.memorizewords.domain.study.repository.record.StudyRecordQuery
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetWeeklyWordStatsUseCase @Inject constructor(
    private val studyRecordQuery: StudyRecordQuery
) {
    operator fun invoke(startDate: String, endDate: String): Flow<List<DailyWordStats>> {
        return studyRecordQuery.getDailyWordStats(startDate, endDate)
    }
}
