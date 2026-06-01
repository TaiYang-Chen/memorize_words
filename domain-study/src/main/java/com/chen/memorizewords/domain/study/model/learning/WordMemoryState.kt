package com.chen.memorizewords.domain.study.model.learning
data class WordMemoryState(
    val wordId: Long,
    val status: WordSessionStatus = WordSessionStatus.UNSEEN,
    val consecutiveCorrect: Int = 0,
    val hasWrongHistory: Boolean = false
)
