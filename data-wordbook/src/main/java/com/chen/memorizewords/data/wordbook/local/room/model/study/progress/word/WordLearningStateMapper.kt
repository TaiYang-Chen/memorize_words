package com.chen.memorizewords.data.wordbook.local.room.model.study.progress.word

import com.chen.memorizewords.domain.study.model.progress.word.WordLearningState

/**
 * 灏嗗疄浣撶被杞崲涓洪鍩熸ā锟?
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
 * 灏嗛鍩熸ā鍨嬭浆鎹负瀹炰綋锟?
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
    