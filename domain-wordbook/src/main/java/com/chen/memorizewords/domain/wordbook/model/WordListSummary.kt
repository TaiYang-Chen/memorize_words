package com.chen.memorizewords.domain.wordbook.model

data class WordListSummary(
    val totalCount: Int = 0,
    val learnedCount: Int = 0,
    val masteredCount: Int = 0,
    val reviewDueCount: Int = 0,
    val favoriteCount: Int = 0
)
