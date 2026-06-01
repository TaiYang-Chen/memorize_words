package com.chen.memorizewords.domain.wordbook.model
data class WordBookUpdateSummary(
    val addedCount: Int,
    val modifiedCount: Int,
    val removedCount: Int,
    val sampleWords: List<String>
)
