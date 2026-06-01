package com.chen.memorizewords.domain.study.usecase.word.study
import com.chen.memorizewords.domain.study.model.record.CheckInRecord
import com.chen.memorizewords.domain.study.repository.record.LearningRecordRepository
import javax.inject.Inject

class AutoCheckInTodayIfEligibleUseCase @Inject constructor(
    private val learningRecordRepository: LearningRecordRepository
) {
    suspend operator fun invoke(): Result<CheckInRecord?> {
        return learningRecordRepository.autoCheckInTodayIfEligible()
    }
}
