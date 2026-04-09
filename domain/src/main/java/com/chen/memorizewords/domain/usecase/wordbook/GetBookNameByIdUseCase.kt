package com.chen.memorizewords.domain.usecase.wordbook

import com.chen.memorizewords.domain.repository.WordBookRepository
import javax.inject.Inject

class GetBookNameByIdUseCase @Inject constructor(
    private val repository: WordBookRepository
) {
    suspend operator fun invoke(bookId: Long): String? = repository.getBookNameById(bookId)
}
