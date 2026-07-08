package com.chen.memorizewords.domain.study.model.learning

import com.chen.memorizewords.domain.word.model.word.Word

data class RecordLearningEventCommand(
    val bookId: Long,
    val word: Word,
    val action: LearningEventAction,
    val quality: Int? = null,
    val correct: Boolean? = null,
    val isNewWordOverride: Boolean? = null,
    val businessDate: String,
    val occurredAt: Long = System.currentTimeMillis(),
    val payloadJson: String? = null
)

data class RecordLearningEventResult(
    val clientEventId: String,
    val wordId: Long,
    val bookId: Long,
    val stateRevision: Long,
    val progressRevision: Long
)
