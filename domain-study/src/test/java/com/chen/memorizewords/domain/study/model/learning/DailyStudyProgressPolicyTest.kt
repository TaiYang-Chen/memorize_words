package com.chen.memorizewords.domain.study.model.learning

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DailyStudyProgressPolicyTest {
    @Test
    fun `fourteenth new word is not eligible for fifteen word target`() {
        val result = decide(newCount = 14, reviewCount = 0, newTarget = 15, reviewTarget = 30)
        assertFalse(result.isNewPlanCompleted)
        assertFalse(result.isReviewPlanCompleted)
        assertFalse(result.isCheckInEligible)
    }

    @Test
    fun `new target alone makes day eligible`() {
        val result = decide(newCount = 15, reviewCount = 0, newTarget = 15, reviewTarget = 30)
        assertTrue(result.isNewPlanCompleted)
        assertFalse(result.isReviewPlanCompleted)
        assertTrue(result.isCheckInEligible)
    }

    @Test
    fun `review target alone makes day eligible`() {
        val result = decide(newCount = 0, reviewCount = 30, newTarget = 15, reviewTarget = 30)
        assertFalse(result.isNewPlanCompleted)
        assertTrue(result.isReviewPlanCompleted)
        assertTrue(result.isCheckInEligible)
    }

    @Test
    fun `both targets can complete together`() {
        val result = decide(newCount = 15, reviewCount = 30, newTarget = 15, reviewTarget = 30)
        assertTrue(result.isNewPlanCompleted)
        assertTrue(result.isReviewPlanCompleted)
    }

    @Test
    fun `zero targets never auto complete`() {
        val result = decide(newCount = 100, reviewCount = 100, newTarget = 0, reviewTarget = 0)
        assertFalse(result.isCheckInEligible)
    }

    @Test
    fun `lowering target completes immediately while raising cannot revoke completion`() {
        val lowered = decide(newCount = 10, reviewCount = 0, newTarget = 10, reviewTarget = 30)
        assertTrue(lowered.isNewPlanCompleted)

        val raised = evaluateDailyPlanCompletion(
            existingNewCompleted = lowered.isNewPlanCompleted,
            existingReviewCompleted = lowered.isReviewPlanCompleted,
            counts = DailyWordCounts(newCount = 10, reviewCount = 0),
            targets = DailyPlanTargets(newTarget = 20, reviewTarget = 30)
        )
        assertTrue(raised.isNewPlanCompleted)
        assertTrue(raised.isCheckInEligible)
    }

    private fun decide(
        newCount: Int,
        reviewCount: Int,
        newTarget: Int,
        reviewTarget: Int
    ): DailyPlanCompletionDecision = evaluateDailyPlanCompletion(
        existingNewCompleted = false,
        existingReviewCompleted = false,
        counts = DailyWordCounts(newCount, reviewCount),
        targets = DailyPlanTargets(newTarget, reviewTarget)
    )
}
