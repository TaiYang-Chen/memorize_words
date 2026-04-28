package com.chen.memorizewords.feature.learning.ui.practice.listening.policy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReviewQueuePolicyTest {

    @Test
    fun `selectNext alternates new words and review words when both queues exist`() {
        val policy = ReviewQueuePolicy()

        policy.reset(listOf(1L, 2L, 3L))
        policy.enqueueReview(10L)
        policy.enqueueReview(11L)

        assertEquals(ListeningQueueSelection(1L, ListeningQueueType.NEW), policy.selectNext())
        assertEquals(ListeningQueueSelection(10L, ListeningQueueType.REVIEW), policy.selectNext())
        assertEquals(ListeningQueueSelection(2L, ListeningQueueType.NEW), policy.selectNext())
        assertEquals(ListeningQueueSelection(11L, ListeningQueueType.REVIEW), policy.selectNext())
        assertEquals(ListeningQueueSelection(3L, ListeningQueueType.NEW), policy.selectNext())
        assertNull(policy.selectNext())
    }

    @Test
    fun `enqueueReview deduplicates and can prioritize skipped words`() {
        val policy = ReviewQueuePolicy()

        policy.reset(emptyList())
        policy.enqueueReview(1L)
        policy.enqueueReview(2L)
        policy.enqueueReview(1L)

        assertEquals(2, policy.reviewCount())
        assertEquals(ListeningQueueSelection(1L, ListeningQueueType.REVIEW), policy.selectNext())
        assertEquals(ListeningQueueSelection(2L, ListeningQueueType.REVIEW), policy.selectNext())

        policy.enqueueReview(3L)
        policy.enqueueReview(4L)
        policy.enqueueReview(5L, prioritize = true)
        assertEquals(ListeningQueueSelection(5L, ListeningQueueType.REVIEW), policy.selectNext())
    }

    @Test
    fun `review goal requires three consecutive correct answers by default`() {
        val policy = ReviewQueuePolicy()

        assertFalse(policy.isReviewGoalMet(2))
        assertTrue(policy.isReviewGoalMet(3))
    }
}
