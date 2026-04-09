package com.chen.memorizewords.domain.usecase.word.study

import com.chen.memorizewords.domain.model.study.record.DailyWordStats
import com.chen.memorizewords.domain.repository.record.LearningRecordRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetWeeklyWordStatsUseCase @Inject constructor(
    private val learningRecordRepository: LearningRecordRepository
) {
    operator fun invoke(startDate: String, endDate: String): Flow<List<DailyWordStats>> {
        return learningRecordRepository.getDailyWordStats(startDate, endDate)
    }
}
