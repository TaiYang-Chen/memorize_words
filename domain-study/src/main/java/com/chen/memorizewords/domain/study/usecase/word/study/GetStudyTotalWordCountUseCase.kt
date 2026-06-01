package com.chen.memorizewords.domain.study.usecase.word.study
import com.chen.memorizewords.domain.wordbook.repository.LearningProgressRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetStudyTotalWordCountUseCase @Inject constructor(
    private val learningProgressRepository: LearningProgressRepository
) {
    operator fun invoke(): Flow<Int> {
        return learningProgressRepository.getStudyTotalWordCount()
    }
}
