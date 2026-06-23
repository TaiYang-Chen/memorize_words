package com.chen.memorizewords.feature.floatingreview.ui.floating

import com.chen.memorizewords.domain.floating.model.FloatingDockConfig
import com.chen.memorizewords.domain.floating.model.FloatingDockEdge
import com.chen.memorizewords.domain.floating.model.FloatingDockState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FloatingDockManagerTest {

    private val manager = FloatingDockManager()
    private val config = FloatingDockConfig(snapTriggerDistanceDp = 24)
    private val bounds = manager.createBounds(
        safeArea = FloatingAvailableArea(left = 0, top = 10, right = 300, bottom = 610),
        ballWidthPx = 80,
        ballHeightPx = 100,
        config = config
    )

    @Test
    fun `free resting state near left edge keeps visible position without dock state`() {
        val result = manager.resolveFreeRestingState(
            bounds = bounds,
            x = bounds.freeLeft + 8,
            y = 120
        )

        assertEquals(FloatingBallPosition(x = bounds.freeLeft + 8, y = 120), result.position)
        assertNull(result.dockState)
    }

    @Test
    fun `free resting state near right edge keeps visible position without dock state`() {
        val result = manager.resolveFreeRestingState(
            bounds = bounds,
            x = bounds.freeRight - 8,
            y = 240
        )

        assertEquals(FloatingBallPosition(x = bounds.freeRight - 8, y = 240), result.position)
        assertNull(result.dockState)
    }

    @Test
    fun `free resting state clamps outside coordinates without docking`() {
        val result = manager.resolveFreeRestingState(
            bounds = bounds,
            x = bounds.freeRight + 200,
            y = bounds.freeTop - 200
        )

        assertEquals(FloatingBallPosition(x = bounds.freeRight, y = bounds.freeTop), result.position)
        assertNull(result.dockState)
    }

    @Test
    fun `existing dock state still resolves to half hidden position`() {
        val result = manager.resolveDocked(
            bounds = bounds,
            config = config,
            dockState = FloatingDockState(
                dockedEdge = FloatingDockEdge.RIGHT,
                crossAxisPercent = 0.5f
            )
        )

        assertEquals(
            FloatingDockResult(
                position = FloatingBallPosition(x = bounds.dockedRight, y = 260),
                dockState = FloatingDockState(
                    dockedEdge = FloatingDockEdge.RIGHT,
                    crossAxisPercent = 0.5f
                )
            ),
            result
        )
    }
}
