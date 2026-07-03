package com.chen.memorizewords.feature.floatingreview.ui.floating

import kotlin.test.Test
import kotlin.test.assertEquals

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
}
