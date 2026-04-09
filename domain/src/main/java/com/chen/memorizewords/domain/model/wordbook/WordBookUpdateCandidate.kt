package com.chen.memorizewords.domain.model.wordbook

data class WordBookUpdateCandidate(
    val bookId: Long,
    val bookName: String,
    val currentVersion: Long,
    val targetVersion: Long,
    val publishedAt: Long,
    val summary: WordBookUpdateSummary,
    val applyMode: WordBookUpdateApplyMode,
    val importance: WordBookUpdateImportance = WordBookUpdateImportance.NORMAL,
    val detailAvailable: Boolean = false,
    val estimatedDownloadBytes: Long = 0L,
    val forcePrompt: Boolean = false,
    val silentAllowed: Boolean = false
)
