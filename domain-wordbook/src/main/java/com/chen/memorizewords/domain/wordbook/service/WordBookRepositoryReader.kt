package com.chen.memorizewords.domain.wordbook.service
import com.chen.memorizewords.domain.wordbook.model.WordBook
import com.chen.memorizewords.domain.wordbook.repository.WordBookRepository
import javax.inject.Inject

class WordBookRepositoryReader @Inject constructor(
    private val wordBookRepository: WordBookRepository
) {
    suspend fun getCurrentWordBook(): WordBook? = wordBookRepository.getCurrentWordBook()

    suspend fun getBookNameById(bookId: Long): String? = wordBookRepository.getBookNameById(bookId)
}
