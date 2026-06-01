package com.chen.memorizewords.feature.floatingreview.ui.floating

import com.chen.memorizewords.domain.floating.model.FloatingDockEdge
import com.chen.memorizewords.domain.floating.model.FloatingDockState
import com.chen.memorizewords.domain.floating.model.FloatingWordSettings
import org.junit.Assert.assertEquals
import org.junit.Test

class FloatingWordServiceTest {

    @Test
    fun `resolveBallAlpha converts percent to alpha`() {
        assertEquals(0f, resolveBallAlpha(0), 0.0001f)
        assertEquals(0.4f, resolveBallAlpha(40), 0.0001f)
        assertEquals(1f, resolveBallAlpha(100), 0.0001f)
    }

    @Test
    fun `resolveBallAlpha clamps out of range values`() {
        assertEquals(0f, resolveBallAlpha(-10), 0.0001f)
        assertEquals(1f, resolveBallAlpha(120), 0.0001f)
    }

    @Test
    fun `resolveCardAlpha converts percent to alpha`() {
        assertEquals(0f, resolveCardAlpha(0), 0.0001f)
        assertEquals(0.5f, resolveCardAlpha(50), 0.0001f)
        assertEquals(1f, resolveCardAlpha(100), 0.0001f)
    }

    @Test
    fun `resolveCardAlpha clamps out of range values`() {
        assertEquals(0f, resolveCardAlpha(-20), 0.0001f)
        assertEquals(1f, resolveCardAlpha(180), 0.0001f)
    }

    @Test
    fun `resolveBallPositionForSettings ignores dock state and uses saved free position`() {
        val position = resolveBallPositionForSettings(
            settings = FloatingWordSettings(
                floatingBallX = -24,
                floatingBallY = 132,
                dockState = FloatingDockState(
                    dockedEdge = FloatingDockEdge.LEFT,
                    crossAxisPercent = 0.2f
                )
            ),
            bounds = FloatingMovementBounds(
                freeLeft = 20,
                freeTop = 40,
                freeRight = 220,
                freeBottom = 320,
                dockedLeft = -2,
                dockedRight = 198,
                visibleWidth = 22,
                hiddenWidth = 22
            ),
            previousBounds = null
        )

        assertEquals(FloatingBallPosition(x = 20, y = 132), position)
    }

    @Test
    fun `resolveBallPositionForSettings uses free default when no saved position exists`() {
        val position = resolveBallPositionForSettings(
            settings = FloatingWordSettings(
                dockState = FloatingDockState(
                    dockedEdge = FloatingDockEdge.RIGHT,
                    crossAxisPercent = 0.8f
                )
            ),
            bounds = FloatingMovementBounds(
                freeLeft = 16,
                freeTop = 24,
                freeRight = 216,
                freeBottom = 324,
                dockedLeft = -6,
                dockedRight = 194,
                visibleWidth = 22,
                hiddenWidth = 22
            ),
            previousBounds = null
        )

        assertEquals(FloatingBallPosition(x = 216, y = 174), position)
    }

    @Test
    fun `resolveBallPositionForSettings keeps edge anchoring during bounds changes`() {
        val position = resolveBallPositionForSettings(
            settings = FloatingWordSettings(
                floatingBallX = 120,
                floatingBallY = 50
            ),
            bounds = FloatingMovementBounds(
                freeLeft = 10,
                freeTop = 0,
                freeRight = 220,
                freeBottom = 200,
                dockedLeft = -12,
                dockedRight = 198,
                visibleWidth = 22,
                hiddenWidth = 22
            ),
            previousBounds = FloatingMovementBounds(
                freeLeft = 0,
                freeTop = 0,
                freeRight = 120,
                freeBottom = 100,
                dockedLeft = -12,
                dockedRight = 98,
                visibleWidth = 22,
                hiddenWidth = 22
            )
        )

        assertEquals(FloatingBallPosition(x = 220, y = 100), position)
    }
}
