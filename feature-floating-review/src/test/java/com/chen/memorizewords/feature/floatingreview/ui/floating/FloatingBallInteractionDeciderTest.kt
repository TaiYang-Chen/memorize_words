package com.chen.memorizewords.feature.floatingreview.ui.floating

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FloatingBallInteractionDeciderTest {

    @Test
    fun `single tap restores current card when card is hidden and word exists`() {
        assertEquals(
            FloatingBallSingleTapAction.ShowCard,
            resolveSingleTapAction(isCardVisible = false, hasCurrentWord = true)
        )
    }

    @Test
    fun `single tap advances when card is hidden and no word exists`() {
        assertEquals(
            FloatingBallSingleTapAction.ShowNextCard,
            resolveSingleTapAction(isCardVisible = false, hasCurrentWord = false)
        )
    }

    @Test
    fun `single tap hides card when card is visible`() {
        assertEquals(
            FloatingBallSingleTapAction.HideCard,
            resolveSingleTapAction(isCardVisible = true, hasCurrentWord = true)
        )
    }

    @Test
    fun `card close button hides card without stopping floating`() {
        assertEquals(
            FloatingCardCloseAction.HideCard,
            resolveCardCloseAction()
        )
    }

    @Test
    fun `rapid second tap is suppressed without delaying the first tap`() {
        assertFalse(
            isRapidRepeatTap(
                previousEventTimeMillis = null,
                eventTimeMillis = 1_000L,
                suppressionWindowMillis = 300L
            )
        )
        assertTrue(
            isRapidRepeatTap(
                previousEventTimeMillis = 1_000L,
                eventTimeMillis = 1_300L,
                suppressionWindowMillis = 300L
            )
        )
        assertFalse(
            isRapidRepeatTap(
                previousEventTimeMillis = 1_000L,
                eventTimeMillis = 1_301L,
                suppressionWindowMillis = 300L
            )
        )
    }

    @Test
    fun `suppressed tap does not extend the suppression window`() {
        var lastAcceptedTap: Long? = null

        fun acceptTap(eventTimeMillis: Long): Boolean {
            val suppressed = isRapidRepeatTap(
                previousEventTimeMillis = lastAcceptedTap,
                eventTimeMillis = eventTimeMillis,
                suppressionWindowMillis = 300L
            )
            if (!suppressed) lastAcceptedTap = eventTimeMillis
            return !suppressed
        }

        assertTrue(acceptTap(1_000L))
        assertFalse(acceptTap(1_250L))
        assertTrue(acceptTap(1_500L))
    }
}
