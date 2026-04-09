package com.chen.memorizewords.domain.model.wordbook

data class WordBookUpdateSummary(
    val addedCount: Int,
    val modifiedCount: Int,
    val removedCount: Int,
    val sampleWords: List<String>
)
