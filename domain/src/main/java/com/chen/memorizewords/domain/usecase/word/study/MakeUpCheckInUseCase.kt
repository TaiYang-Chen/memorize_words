package com.chen.memorizewords.domain.usecase.word.study

import com.chen.memorizewords.domain.model.study.record.CheckInRecord
import com.chen.memorizewords.domain.repository.record.LearningRecordRepository
import javax.inject.Inject

class MakeUpCheckInUseCase @Inject constructor(
    private val learningRecordRepository: LearningRecordRepository
) {
    suspend operator fun invoke(date: String): Result<CheckInRecord> {
        return learningRecordRepository.makeUpCheckIn(date)
    }
}
