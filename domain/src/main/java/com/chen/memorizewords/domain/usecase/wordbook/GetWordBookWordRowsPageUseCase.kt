package com.chen.memorizewords.domain.usecase.wordbook

import com.chen.memorizewords.domain.model.common.PageSlice
import com.chen.memorizewords.domain.model.wordbook.WordListQuery
import com.chen.memorizewords.domain.model.words.WordListRow
import com.chen.memorizewords.domain.repository.WordBookRepository
import javax.inject.Inject

class GetWordBookWordRowsPageUseCase @Inject constructor(
    private val wordBookRepository: WordBookRepository
) {
    suspend operator fun invoke(query: WordListQuery): PageSlice<WordListRow> {
        return wordBookRepository.getWordRowsPage(query)
    }
}
