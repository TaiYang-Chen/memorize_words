package com.chen.memorizewords.domain.study.usecase.word.study
import com.chen.memorizewords.domain.study.model.progress.word.WordLearningState
import com.chen.memorizewords.domain.study.repository.WordLearningRepository
import javax.inject.Inject

class GetWordLearningStatesByBookIdUseCase @Inject constructor(
    private val wordLearningRepository: WordLearningRepository
) {
    suspend operator fun invoke(bookId: Long): List<WordLearningState> {
        return wordLearningRepository.getLearningStatesByBookId(bookId)
    }
}
