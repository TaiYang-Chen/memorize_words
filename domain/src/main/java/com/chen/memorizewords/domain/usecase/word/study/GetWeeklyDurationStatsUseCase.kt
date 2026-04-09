package com.chen.memorizewords.domain.usecase.word.study

import com.chen.memorizewords.domain.model.study.record.DailyDurationStats
import com.chen.memorizewords.domain.repository.record.LearningRecordRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetWeeklyDurationStatsUseCase @Inject constructor(
    private val learningRecordRepository: LearningRecordRepository
) {
    operator fun invoke(startDate: String, endDate: String): Flow<List<DailyDurationStats>> {
        return learningRecordRepository.getDailyDurationStats(startDate, endDate)
    }
}
