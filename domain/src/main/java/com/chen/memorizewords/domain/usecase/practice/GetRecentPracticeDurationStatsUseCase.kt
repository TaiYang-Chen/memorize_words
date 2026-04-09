package com.chen.memorizewords.domain.usecase.practice

import com.chen.memorizewords.domain.model.practice.PracticeDailyDurationStats
import com.chen.memorizewords.domain.repository.practice.PracticeRecordRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetRecentPracticeDurationStatsUseCase @Inject constructor(
    private val practiceRecordRepository: PracticeRecordRepository
) {
    operator fun invoke(dayCount: Int): Flow<List<PracticeDailyDurationStats>> {
        return practiceRecordRepository.getRecentPracticeDurationStats(dayCount)
    }
}
