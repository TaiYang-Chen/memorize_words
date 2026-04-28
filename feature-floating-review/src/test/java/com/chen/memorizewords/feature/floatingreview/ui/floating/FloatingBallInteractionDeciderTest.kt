package com.chen.memorizewords.feature.floatingreview.ui.floating

import org.junit.Assert.assertEquals
import org.junit.Test

class FloatingBallInteractionDeciderTest {

    @Test
    fun `single tap shows card when hidden`() {
        assertEquals(
            FloatingBallSingleTapAction.ShowCard,
            resolveSingleTapAction(isCardVisible = false)
        )
    }

    @Test
    fun `single tap hides card when visible`() {
        assertEquals(
            FloatingBallSingleTapAction.HideCard,
            resolveSingleTapAction(isCardVisible = true)
        )
    }
}
