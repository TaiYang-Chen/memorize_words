package com.chen.memorizewords.domain.study.repository.learning

import com.chen.memorizewords.domain.study.model.progress.word.WordLearningState
import com.chen.memorizewords.domain.wordbook.model.study.progress.wordbook.WordBookProgress

data class LearningEventSyncResultSnapshot(
    val clientEventId: String,
    val conflict: Boolean,
    val wordState: WordLearningState?,
    val learningProgress: WordBookProgress?,
    val serverStateRevision: Long
)

interface LearningSyncStatePort {
    suspend fun hasPendingLearningEvents(): Boolean

    suspend fun applyLearningEventSyncResult(result: LearningEventSyncResultSnapshot)
}
