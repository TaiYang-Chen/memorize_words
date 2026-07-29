package com.chen.memorizewords.domain.study.usecase.word.study
import com.chen.memorizewords.domain.study.repository.record.DailyStudyRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetContinuousCheckInDaysUseCase @Inject constructor(
    private val dailyStudyRepository: DailyStudyRepository
) {
    operator fun invoke(): Flow<Int> {
        return dailyStudyRepository.getContinuousCheckInDays()
    }
}
