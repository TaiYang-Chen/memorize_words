package com.chen.memorizewords.domain.wordbook.usecase

import com.chen.memorizewords.domain.wordbook.model.WordListQuery
import com.chen.memorizewords.domain.wordbook.repository.WordBookRepository
import javax.inject.Inject

class GetWordBookWordRowIdsUseCase @Inject constructor(
    private val wordBookRepository: WordBookRepository
) {
    suspend operator fun invoke(query: WordListQuery, limit: Int): List<Long> {
        return wordBookRepository.getWordRowIds(query, limit)
    }
}
