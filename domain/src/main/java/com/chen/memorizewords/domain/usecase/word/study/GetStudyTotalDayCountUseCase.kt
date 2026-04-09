package com.chen.memorizewords.domain.usecase.word.study

import com.chen.memorizewords.domain.repository.LearningProgressRepository
import com.chen.memorizewords.domain.repository.record.LearningRecordRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetStudyTotalDayCountUseCase @Inject constructor(
    private val learningRecordRepository: LearningRecordRepository
) {
    operator fun invoke(): Flow<Int> {
        return learningRecordRepository.getStudyTotalDayCount()
    }
}
