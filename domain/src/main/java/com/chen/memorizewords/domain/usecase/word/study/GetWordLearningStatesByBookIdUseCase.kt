package com.chen.memorizewords.domain.usecase.word.study

import com.chen.memorizewords.domain.model.study.progress.word.WordLearningState
import com.chen.memorizewords.domain.repository.WordLearningRepository
import javax.inject.Inject

class GetWordLearningStatesByBookIdUseCase @Inject constructor(
    private val wordLearningRepository: WordLearningRepository
) {
    suspend operator fun invoke(bookId: Long): List<WordLearningState> {
        return wordLearningRepository.getLearningStatesByBookId(bookId)
    }
}
