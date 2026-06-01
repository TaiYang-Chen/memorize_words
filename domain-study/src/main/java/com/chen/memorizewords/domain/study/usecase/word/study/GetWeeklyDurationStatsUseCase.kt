package com.chen.memorizewords.domain.study.usecase.word.study
import com.chen.memorizewords.domain.study.model.record.DailyDurationStats
import com.chen.memorizewords.domain.study.repository.record.LearningRecordRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetWeeklyDurationStatsUseCase @Inject constructor(
    private val learningRecordRepository: LearningRecordRepository
) {
    operator fun invoke(startDate: String, endDate: String): Flow<List<DailyDurationStats>> {
        return learningRecordRepository.getDailyDurationStats(startDate, endDate)
    }
}
