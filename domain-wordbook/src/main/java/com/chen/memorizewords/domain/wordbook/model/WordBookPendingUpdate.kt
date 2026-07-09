package com.chen.memorizewords.domain.wordbook.model
data class WordBookPendingUpdate(
    val bookId: Long,
    val bookName: String,
    val currentVersion: Long,
    val targetVersion: Long,
    val publishedAtMs: Long,
    val summary: WordBookUpdateSummary,
    val applyMode: WordBookUpdateApplyMode
)
