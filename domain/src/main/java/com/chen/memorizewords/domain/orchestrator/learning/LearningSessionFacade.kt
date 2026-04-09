package com.chen.memorizewords.domain.orchestrator.learning

import com.chen.memorizewords.domain.model.learning.LearningSessionRequest
import com.chen.memorizewords.domain.repository.WordOrderType
import com.chen.memorizewords.domain.usecase.word.GetNewLearnWordsUseCase
import com.chen.memorizewords.domain.usecase.word.GetReviewWordsUseCase
import javax.inject.Inject

class LearningSessionFacade @Inject constructor(
    private val getNewLearnWords: GetNewLearnWordsUseCase,
    private val getReviewWords: GetReviewWordsUseCase
) {
    suspend fun createNewSessionRequest(
        bookId: Long,
        count: Int,
        orderType: WordOrderType = WordOrderType.RANDOM,
        excludeIds: Set<Long> = emptySet(),
        initialLearnedCount: Int = 0
    ): LearningSessionRequest? {
        return createRequest(
            initialLearnedCount = initialLearnedCount,
            sessionType = LearningSessionTypes.NEW,
            sessionWordCount = count.coerceAtLeast(0),
            wordIds = getNewLearnWords(
                bookId = bookId,
                count = count,
                orderType = orderType,
                excludeIds = excludeIds
            ).map { it.id }
        )
    }

    suspend fun createReviewSessionRequest(
        bookId: Long,
        count: Int,
        orderType: WordOrderType = WordOrderType.RANDOM,
        excludeIds: Set<Long> = emptySet(),
        initialLearnedCount: Int = 0
    ): LearningSessionRequest? {
        return createRequest(
            initialLearnedCount = initialLearnedCount,
            sessionType = LearningSessionTypes.REVIEW,
            sessionWordCount = count.coerceAtLeast(0),
            wordIds = getReviewWords(
                bookId = bookId,
                count = count,
                orderType = orderType,
                excludeIds = excludeIds
            ).map { it.id }
        )
    }

    private fun createRequest(
        initialLearnedCount: Int,
        sessionType: Int,
        sessionWordCount: Int,
        wordIds: List<Long>
    ): LearningSessionRequest? {
        if (wordIds.isEmpty()) return null
        return LearningSessionRequest(
            initialLearnedCount = initialLearnedCount,
            wordIds = wordIds,
            sessionType = sessionType,
            sessionWordCount = sessionWordCount
        )
    }
}

object LearningSessionTypes {
    const val NEW = 0
    const val REVIEW = 1
}
