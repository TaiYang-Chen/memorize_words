package com.chen.memorizewords.domain.usecase.word.study

import com.chen.memorizewords.domain.model.study.record.CheckInRecord
import com.chen.memorizewords.domain.repository.record.LearningRecordRepository
import javax.inject.Inject

class AutoCheckInTodayIfEligibleUseCase @Inject constructor(
    private val learningRecordRepository: LearningRecordRepository
) {
    suspend operator fun invoke(): Result<CheckInRecord?> {
        return learningRecordRepository.autoCheckInTodayIfEligible()
    }
}
