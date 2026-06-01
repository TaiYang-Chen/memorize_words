package com.chen.memorizewords.domain.wordbook.usecase
import com.chen.memorizewords.core.common.paging.PageSlice
import com.chen.memorizewords.domain.wordbook.model.WordListQuery
import com.chen.memorizewords.domain.word.model.WordListRow
import com.chen.memorizewords.domain.wordbook.repository.WordBookRepository
import javax.inject.Inject

class GetWordBookWordRowsPageUseCase @Inject constructor(
    private val wordBookRepository: WordBookRepository
) {
    suspend operator fun invoke(query: WordListQuery): PageSlice<WordListRow> {
        return wordBookRepository.getWordRowsPage(query)
    }
}
