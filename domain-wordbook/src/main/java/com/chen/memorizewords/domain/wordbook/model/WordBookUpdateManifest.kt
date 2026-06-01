package com.chen.memorizewords.domain.wordbook.model
data class WordBookUpdateManifest(
    val bookId: Long,
    val targetVersion: Long,
    val applyMode: WordBookUpdateApplyMode,
    val removedWordIds: List<Long>,
    val upsertWordCount: Int,
    val pageSize: Int
)
