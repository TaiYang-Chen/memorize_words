package com.chen.memorizewords.domain.usecase.word.study

import com.chen.memorizewords.domain.repository.record.LearningRecordRepository
import javax.inject.Inject

class GetCurrentBusinessDateUseCase @Inject constructor(
    private val learningRecordRepository: LearningRecordRepository
) {
    operator fun invoke(): String {
        return learningRecordRepository.getCurrentBusinessDate()
    }
}
