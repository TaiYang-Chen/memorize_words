package com.chen.memorizewords.feature.floatingreview.ui.floating

import com.chen.memorizewords.domain.floating.model.FloatingDockConfig
import com.chen.memorizewords.domain.floating.model.FloatingDockEdge
import com.chen.memorizewords.domain.floating.model.FloatingDockState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FloatingDockManagerTest {

    private val manager = FloatingDockManager()
    private val config = FloatingDockConfig(
        snapTriggerDistanceDp = 24,
        halfHiddenEnabled = true
    )
    private val bounds = manager.createBounds(
        physicalDisplayBounds = FloatingPhysicalDisplayBounds(
            left = 0,
            top = 10,
            right = 300,
            bottom = 610
        ),
        ballWidthPx = 80,
        ballHeightPx = 100
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
    fun `legacy half hidden dock state resolves to a fully visible edge position`() {
        assertEquals(bounds.freeLeft, bounds.dockedLeft)
        assertEquals(bounds.freeRight, bounds.dockedRight)

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
                position = FloatingBallPosition(x = bounds.freeRight, y = 260),
                dockState = FloatingDockState(
                    dockedEdge = FloatingDockEdge.RIGHT,
                    crossAxisPercent = 0.5f
                )
            ),
            result
        )
    }

    @Test
    fun `physical display right edge is reachable with the full pet window visible`() {
        val physicalBounds = manager.createBounds(
            physicalDisplayBounds = FloatingPhysicalDisplayBounds(
                left = 0,
                top = 0,
                right = 1080,
                bottom = 2400
            ),
            ballWidthPx = 215,
            ballHeightPx = 258
        )

        assertEquals(865, physicalBounds.freeRight)
        assertEquals(865, physicalBounds.dockedRight)
        assertEquals(
            FloatingBallPosition(x = 864, y = 0),
            manager.resolveFreeRestingState(physicalBounds, x = 864, y = 0).position
        )
        assertEquals(
            FloatingBallPosition(x = 865, y = 0),
            manager.resolveFreeRestingState(physicalBounds, x = 866, y = 0).position
        )
    }
}
