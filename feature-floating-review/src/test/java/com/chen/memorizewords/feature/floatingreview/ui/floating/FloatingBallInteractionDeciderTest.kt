package com.chen.memorizewords.feature.floatingreview.ui.floating

import kotlin.test.Test
import kotlin.test.assertEquals

class FloatingBallInteractionDeciderTest {

    @Test
    fun `single tap shows card when card is hidden`() {
        assertEquals(
            FloatingBallSingleTapAction.ShowCard,
            resolveSingleTapAction(isCardVisible = false)
        )
    }

    @Test
    fun `single tap hides card when card is visible`() {
        assertEquals(
            FloatingBallSingleTapAction.HideCard,
            resolveSingleTapAction(isCardVisible = true)
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
