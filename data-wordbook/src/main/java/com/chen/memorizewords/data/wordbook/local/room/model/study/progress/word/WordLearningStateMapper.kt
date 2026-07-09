package com.chen.memorizewords.data.wordbook.local.room.model.study.progress.word

import com.chen.memorizewords.domain.study.model.progress.word.WordLearningState

/**
 * 将实体类转换为领域模型。
 */
fun WordLearningStateEntity.toDomain(): WordLearningState {
    return WordLearningState(
        wordId = wordId,
        bookId = bookId,
        totalLearnCount = totalLearnCount,
        lastLearnedAtMs = lastLearnedAtMs,
        nextReviewAtMs = nextReviewAtMs,
        masteryLevel = masteryLevel,
        userStatus = userStatus,
        interval = interval,
        repetition = repetition,
        efactor = efactor,
        stateRevision = stateRevision,
        lastEventId = lastEventId
    )
}

/**
 * 将领域模型转换为实体。
 */
fun WordLearningState.toEntity(): WordLearningStateEntity {
    return WordLearningStateEntity(
        wordId = wordId,
        bookId = bookId,
        totalLearnCount = totalLearnCount,
        lastLearnedAtMs = lastLearnedAtMs,
        nextReviewAtMs = nextReviewAtMs,
        masteryLevel = masteryLevel,
        userStatus = userStatus,
        interval = interval,
        repetition = repetition,
        efactor = efactor,
        stateRevision = stateRevision,
        lastEventId = lastEventId
    )
}
