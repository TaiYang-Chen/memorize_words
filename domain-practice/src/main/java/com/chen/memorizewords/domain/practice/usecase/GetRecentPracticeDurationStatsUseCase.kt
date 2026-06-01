package com.chen.memorizewords.domain.practice.usecase
import com.chen.memorizewords.domain.practice.PracticeDailyDurationStats
import com.chen.memorizewords.domain.practice.PracticeRecordRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetRecentPracticeDurationStatsUseCase @Inject constructor(
    private val practiceRecordRepository: PracticeRecordRepository
) {
    operator fun invoke(dayCount: Int): Flow<List<PracticeDailyDurationStats>> {
        return practiceRecordRepository.getRecentPracticeDurationStats(dayCount)
    }
}
