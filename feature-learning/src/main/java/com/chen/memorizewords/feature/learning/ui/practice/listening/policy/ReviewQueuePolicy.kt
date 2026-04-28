package com.chen.memorizewords.feature.learning.ui.practice.listening.policy

import com.chen.memorizewords.feature.learning.ui.practice.listening.LISTENING_REVIEW_TARGET

internal enum class ListeningQueueType {
    NEW,
    REVIEW
}

internal data class ListeningQueueSelection(
    val wordId: Long,
    val queueType: ListeningQueueType
)

internal class ReviewQueuePolicy {
    private val newQueue = ArrayDeque<Long>()
    private val reviewQueue = ArrayDeque<Long>()
    private var lastQueueType: ListeningQueueType? = null

    fun reset(newWordIds: List<Long>) {
        newQueue.clear()
        reviewQueue.clear()
        lastQueueType = null
        newWordIds.forEach(newQueue::addLast)
    }

    fun isEmpty(): Boolean = newQueue.isEmpty() && reviewQueue.isEmpty()

    fun reviewCount(): Int = reviewQueue.size

    fun enqueueReview(wordId: Long, prioritize: Boolean = false) {
        val existed = reviewQueue.remove(wordId)
        if (existed || prioritize) {
            reviewQueue.addFirst(wordId)
        } else {
            reviewQueue.addLast(wordId)
        }
    }

    fun isReviewGoalMet(
        consecutiveCorrect: Int,
        target: Int = LISTENING_REVIEW_TARGET
    ): Boolean {
        return consecutiveCorrect >= target
    }

    fun selectNext(): ListeningQueueSelection? {
        if (isEmpty()) return null
        val queueType = when {
            newQueue.isNotEmpty() && reviewQueue.isNotEmpty() -> {
                if (lastQueueType == ListeningQueueType.NEW) {
                    ListeningQueueType.REVIEW
                } else {
                    ListeningQueueType.NEW
                }
            }
            newQueue.isNotEmpty() -> ListeningQueueType.NEW
            else -> ListeningQueueType.REVIEW
        }
        lastQueueType = queueType
        val wordId = when (queueType) {
            ListeningQueueType.NEW -> newQueue.removeFirst()
            ListeningQueueType.REVIEW -> reviewQueue.removeFirst()
        }
        return ListeningQueueSelection(wordId, queueType)
    }
}
