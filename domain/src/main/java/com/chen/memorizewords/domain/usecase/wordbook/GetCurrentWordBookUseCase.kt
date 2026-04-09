package com.chen.memorizewords.domain.usecase.wordbook

import com.chen.memorizewords.domain.model.wordbook.WordBook
import com.chen.memorizewords.domain.repository.WordBookRepository
import javax.inject.Inject

class GetCurrentWordBookUseCase @Inject constructor(
    private val repository: WordBookRepository
) {
    suspend operator fun invoke(): WordBook? = repository.getCurrentWordBook()
}
