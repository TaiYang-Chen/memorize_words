package com.chen.memorizewords.domain.wordbook.usecase
import com.chen.memorizewords.domain.wordbook.repository.WordBookRepository
import javax.inject.Inject

class GetBookNameByIdUseCase @Inject constructor(
    private val repository: WordBookRepository
) {
    suspend operator fun invoke(bookId: Long): String? = repository.getBookNameById(bookId)
}
