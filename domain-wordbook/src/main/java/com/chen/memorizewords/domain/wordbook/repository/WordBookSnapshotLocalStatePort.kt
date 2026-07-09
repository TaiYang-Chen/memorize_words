package com.chen.memorizewords.domain.wordbook.repository

import com.chen.memorizewords.domain.wordbook.model.study.progress.wordbook.WordBookProgress

data class WordBookLearningStateSnapshot(
    val wordId: Long,
    val bookId: Long,
    val totalLearnCount: Int,
    val lastLearnedAtMs: Long,
    val nextReviewAtMs: Long,
    val masteryLevel: Int,
    val userStatus: Int,
    val repetition: Int,
    val interval: Long,
    val efactor: Double
)

interface WordBookSnapshotLocalStatePort {
    suspend fun overwriteLearningStatesForBookFromRemote(
        bookId: Long,
        states: List<WordBookLearningStateSnapshot>
    )

    suspend fun overwriteProgressFromRemote(progress: List<WordBookProgress>)

    suspend fun upsertProgressFromRemote(progress: List<WordBookProgress>)
}
