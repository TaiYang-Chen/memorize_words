package com.chen.memorizewords.domain.wordbook.model
import com.chen.memorizewords.domain.word.model.enums.WordFilter

data class WordListQuery(
    val wordBookId: Long,
    val pageIndex: Int,
    val pageSize: Int,
    val filter: WordFilter = WordFilter.ALL
)
