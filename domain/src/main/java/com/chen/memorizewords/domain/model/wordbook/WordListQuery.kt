package com.chen.memorizewords.domain.model.wordbook

import com.chen.memorizewords.domain.model.words.enums.WordFilter

data class WordListQuery(
    val wordBookId: Long,
    val pageIndex: Int,
    val pageSize: Int,
    val filter: WordFilter = WordFilter.ALL
)
