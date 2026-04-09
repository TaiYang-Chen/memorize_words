package com.chen.memorizewords.domain.model.wordbook

data class WordBookPendingUpdate(
    val bookId: Long,
    val bookName: String,
    val currentVersion: Long,
    val targetVersion: Long,
    val publishedAt: Long,
    val summary: WordBookUpdateSummary,
    val applyMode: WordBookUpdateApplyMode
)
