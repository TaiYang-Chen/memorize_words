package com.chen.memorizewords.domain.wordbook.usecase
import com.chen.memorizewords.domain.wordbook.repository.WordBookRepository
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