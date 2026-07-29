package com.chen.memorizewords.domain.study.usecase.word.study

import com.chen.memorizewords.domain.study.model.record.CheckInRecord
import com.chen.memorizewords.domain.study.repository.record.DailyStudyRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class ObserveCheckInRecordUseCase @Inject constructor(
    private val dailyStudyRepository: DailyStudyRepository
) {
    operator fun invoke(businessDate: String): Flow<CheckInRecord?> =
        dailyStudyRepository.observeCheckInRecord(businessDate)
}
