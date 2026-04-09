package com.chen.memorizewords.domain.service.wordbook

import com.chen.memorizewords.domain.model.wordbook.WordBook
import com.chen.memorizewords.domain.repository.WordBookRepository
import javax.inject.Inject

class WordBookRepositoryReader @Inject constructor(
    private val wordBookRepository: WordBookRepository
) {
    suspend fun getCurrentWordBook(): WordBook? = wordBookRepository.getCurrentWordBook()

    suspend fun getBookNameById(bookId: Long): String? = wordBookRepository.getBookNameById(bookId)
}
