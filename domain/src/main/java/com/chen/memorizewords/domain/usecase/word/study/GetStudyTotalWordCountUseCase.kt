package com.chen.memorizewords.domain.usecase.word.study

import com.chen.memorizewords.domain.repository.LearningProgressRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetStudyTotalWordCountUseCase @Inject constructor(
    private val learningProgressRepository: LearningProgressRepository
) {
    operator fun invoke(): Flow<Int> {
        return learningProgressRepository.getStudyTotalWordCount()
    }
}
