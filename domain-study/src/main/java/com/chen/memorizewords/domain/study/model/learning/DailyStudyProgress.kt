package com.chen.memorizewords.domain.study.model.learning

import com.chen.memorizewords.domain.study.model.record.CheckInRecord

data class DailyWordCounts(
    val newCount: Int,
    val reviewCount: Int
) {
    init {
        require(newCount >= 0) { "newCount must be non-negative" }
        require(reviewCount >= 0) { "reviewCount must be non-negative" }
    }
}

data class DailyPlanTargets(
    val newTarget: Int,
    val reviewTarget: Int
) {
    fun normalized(): DailyPlanTargets = copy(
        newTarget = newTarget.coerceAtLeast(0),
        reviewTarget = reviewTarget.coerceAtLeast(0)
    )
}

data class DailyProgressProjectionTask(
    val clientEventId: String,
    val clientSequence: Long,
    val businessDate: String,
    val counts: DailyWordCounts,
    val targets: DailyPlanTargets,
    val createdAtMs: Long
)

data class DailyProgressEvaluation(
    val businessDate: String,
    val counts: DailyWordCounts,
    val targets: DailyPlanTargets
)

sealed interface DailyProgressTransition {
    val businessDate: String

    data class NotEligible(override val businessDate: String) : DailyProgressTransition

    data class CheckInCreated(
        override val businessDate: String,
        val record: CheckInRecord
    ) : DailyProgressTransition

    data class AlreadyCheckedIn(
        override val businessDate: String,
        val record: CheckInRecord
    ) : DailyProgressTransition

    data class RecoveryRequired(override val businessDate: String) : DailyProgressTransition
}

data class DailyPlanCompletionDecision(
    val isNewPlanCompleted: Boolean,
    val isReviewPlanCompleted: Boolean
) {
    val isCheckInEligible: Boolean
        get() = isNewPlanCompleted || isReviewPlanCompleted
}

fun evaluateDailyPlanCompletion(
    existingNewCompleted: Boolean,
    existingReviewCompleted: Boolean,
    counts: DailyWordCounts,
    targets: DailyPlanTargets
): DailyPlanCompletionDecision {
    val normalizedTargets = targets.normalized()
    return DailyPlanCompletionDecision(
        isNewPlanCompleted = existingNewCompleted ||
            (normalizedTargets.newTarget > 0 && counts.newCount >= normalizedTargets.newTarget),
        isReviewPlanCompleted = existingReviewCompleted ||
            (normalizedTargets.reviewTarget > 0 && counts.reviewCount >= normalizedTargets.reviewTarget)
    )
}
