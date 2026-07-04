package com.chen.memorizewords.domain.wordbook.model

import com.chen.memorizewords.domain.word.model.enums.WordFilter
import com.chen.memorizewords.domain.word.model.enums.WordSortType

data class WordListQuery(
    val wordBookId: Long,
    val pageIndex: Int,
    val pageSize: Int,
    val filter: WordFilter = WordFilter.ALL,
    val keyword: String = "",
    val sortType: WordSortType = WordSortType.BOOK_ORDER,
    val now: Long = System.currentTimeMillis()
) {
    val normalizedKeyword: String
        get() = keyword.trim()
}
