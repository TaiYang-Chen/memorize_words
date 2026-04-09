package com.chen.memorizewords.domain.usecase.word.study

import com.chen.memorizewords.domain.repository.WordBookRepository
import javax.inject.Inject

class RecordWordAnswerResultUseCase @Inject constructor(
    private val wordBookRepository: WordBookRepository,
    private val getCurrentBusinessDateUseCase: GetCurrentBusinessDateUseCase
) {
    suspend operator fun invoke(bookId: Long, isCorrect: Boolean) {
        if (bookId <= 0L) return
        val today = getCurrentBusinessDateUseCase()
        wordBookRepository.recordAnswerResult(bookId, isCorrect, today)
    }
}
