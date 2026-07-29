package com.chen.memorizewords.domain.study.usecase.word.study
import com.chen.memorizewords.domain.study.repository.record.BusinessDateProvider
import com.chen.memorizewords.domain.study.repository.record.DailyStudyRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetTodayStudyDurationUseCase @Inject constructor(
    private val dailyStudyRepository: DailyStudyRepository,
    private val businessDateProvider: BusinessDateProvider
) {
    operator fun invoke(): Flow<Long> {
        return dailyStudyRepository.getStudyDuration(businessDateProvider.currentBusinessDate())
    }
}
