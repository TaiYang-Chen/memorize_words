package com.chen.memorizewords.domain.wordbook.usecase

import com.chen.memorizewords.domain.wordbook.model.WordBookInfo
import com.chen.memorizewords.domain.wordbook.repository.WordBookRepository
import javax.inject.Inject

class CreateMyWordBookUseCase @Inject constructor(
    private val repository: WordBookRepository
) {
    suspend operator fun invoke(
        title: String,
        category: String,
        description: String,
        words: List<String>
    ): Result<WordBookInfo> {
        return repository.createMyWordBook(
            title = title,
            category = category,
            description = description,
            words = words
        )
    }
}
