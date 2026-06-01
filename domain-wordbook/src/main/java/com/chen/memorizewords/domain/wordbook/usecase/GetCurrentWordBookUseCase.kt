package com.chen.memorizewords.domain.wordbook.usecase
import com.chen.memorizewords.domain.wordbook.model.WordBook
import com.chen.memorizewords.domain.wordbook.repository.WordBookRepository
import javax.inject.Inject

class GetCurrentWordBookUseCase @Inject constructor(
    private val repository: WordBookRepository
) {
    suspend operator fun invoke(): WordBook? = repository.getCurrentWordBook()
}
