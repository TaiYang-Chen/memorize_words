package com.chen.memorizewords.domain.practice
enum class PracticeQueueType {
    NEW,
    REVIEW
}

data class PracticeQueueSelection(
    val wordId: Long,
    val queueType: PracticeQueueType
)

class PracticeReviewQueuePolicy(
    private val reviewTarget: Int = DEFAULT_REVIEW_TARGET
) {
    private val newQueue = ArrayDeque<Long>()
    private val reviewQueue = ArrayDeque<Long>()
    private var lastQueueType: PracticeQueueType? = null

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

    fun isReviewGoalMet(consecutiveCorrect: Int): Boolean {
        return consecutiveCorrect >= reviewTarget
    }

    fun reviewTarget(): Int = reviewTarget

    fun selectNext(): PracticeQueueSelection? {
        if (isEmpty()) return null
        val queueType = when {
            newQueue.isNotEmpty() && reviewQueue.isNotEmpty() -> {
                if (lastQueueType == PracticeQueueType.NEW) {
                    PracticeQueueType.REVIEW
                } else {
                    PracticeQueueType.NEW
                }
            }

            newQueue.isNotEmpty() -> PracticeQueueType.NEW
            else -> PracticeQueueType.REVIEW
        }
        lastQueueType = queueType
        val wordId = when (queueType) {
            PracticeQueueType.NEW -> newQueue.removeFirst()
            PracticeQueueType.REVIEW -> reviewQueue.removeFirst()
        }
        return PracticeQueueSelection(wordId, queueType)
    }

    private companion object {
        const val DEFAULT_REVIEW_TARGET = 3
    }
}
