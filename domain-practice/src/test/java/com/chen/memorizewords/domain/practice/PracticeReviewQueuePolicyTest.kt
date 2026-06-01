package com.chen.memorizewords.domain.practice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PracticeReviewQueuePolicyTest {

    @Test
    fun `selectNext alternates new words and review words when both queues exist`() {
        val policy = PracticeReviewQueuePolicy()

        policy.reset(listOf(1L, 2L, 3L))
        policy.enqueueReview(10L)
        policy.enqueueReview(11L)

        assertEquals(PracticeQueueSelection(1L, PracticeQueueType.NEW), policy.selectNext())
        assertEquals(PracticeQueueSelection(10L, PracticeQueueType.REVIEW), policy.selectNext())
        assertEquals(PracticeQueueSelection(2L, PracticeQueueType.NEW), policy.selectNext())
        assertEquals(PracticeQueueSelection(11L, PracticeQueueType.REVIEW), policy.selectNext())
        assertEquals(PracticeQueueSelection(3L, PracticeQueueType.NEW), policy.selectNext())
        assertNull(policy.selectNext())
    }

    @Test
    fun `enqueueReview deduplicates and can prioritize skipped words`() {
        val policy = PracticeReviewQueuePolicy()

        policy.reset(emptyList())
        policy.enqueueReview(1L)
        policy.enqueueReview(2L)
        policy.enqueueReview(1L)

        assertEquals(2, policy.reviewCount())
        assertEquals(PracticeQueueSelection(1L, PracticeQueueType.REVIEW), policy.selectNext())
        assertEquals(PracticeQueueSelection(2L, PracticeQueueType.REVIEW), policy.selectNext())

        policy.enqueueReview(3L)
        policy.enqueueReview(4L)
        policy.enqueueReview(5L, prioritize = true)
        assertEquals(PracticeQueueSelection(5L, PracticeQueueType.REVIEW), policy.selectNext())
    }

    @Test
    fun `review goal requires three consecutive correct answers by default`() {
        val policy = PracticeReviewQueuePolicy()

        assertFalse(policy.isReviewGoalMet(2))
        assertTrue(policy.isReviewGoalMet(3))
    }
}
