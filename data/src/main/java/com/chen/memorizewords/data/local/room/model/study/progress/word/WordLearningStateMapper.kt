package com.chen.memorizewords.data.local.room.model.study.progress.word

import com.chen.memorizewords.domain.model.study.progress.word.WordLearningState

/**
 * 将实体类转换为领域模型
 */
fun WordLearningStateEntity.toDomain(): WordLearningState {
    return WordLearningState(
        wordId = wordId,
        bookId = bookId,
        totalLearnCount = totalLearnCount,
        lastLearnTime = lastLearnTime,
        nextReviewTime = nextReviewTime,
        masteryLevel = masteryLevel,
        userStatus = userStatus,
        interval = interval,
        repetition = repetition,
        efactor = efactor
    )
}

/**
 * 将领域模型转换为实体类
 */
fun WordLearningState.toEntity(): WordLearningStateEntity {
    return WordLearningStateEntity(
        wordId = wordId,
        bookId = bookId,
        totalLearnCount = totalLearnCount,
        lastLearnTime = lastLearnTime,
        nextReviewTime = nextReviewTime,
        masteryLevel = masteryLevel,
        userStatus = userStatus,
        interval = interval,
        repetition = repetition,
        efactor = efactor
    )
}
    