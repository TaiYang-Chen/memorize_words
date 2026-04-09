package com.chen.memorizewords.domain.usecase.wordbook

import com.chen.memorizewords.domain.repository.WordBookRepository
import javax.inject.Inject

class SetCurrentWordBookUseCase @Inject constructor(
    private val wordBookRepository: WordBookRepository
) {
    suspend operator fun invoke(
        bookId: Long
    ) {
        return wordBookRepository.setCurrentWordBook(bookId)
    }
}