package com.chen.memorizewords.domain.wordbook.model

data class WordBookContentState(
    val bookId: Long,
    val targetVersion: Long,
    val localVersion: Long,
    val status: WordBookContentStatus,
    val downloadedWords: Int,
    val totalWords: Int,
    val lastError: String?
)

enum class WordBookContentStatus {
    MISSING,
    DOWNLOADING,
    READY,
    FAILED
}
