package com.chen.memorizewords.feature.floatingreview.ui.floating

import com.chen.memorizewords.domain.floating.model.FloatingDockConfig
import com.chen.memorizewords.domain.floating.model.FloatingDockEdge
import com.chen.memorizewords.domain.floating.model.FloatingDockState
import kotlin.math.roundToInt

/** Physical display coordinates used exclusively to constrain the pet render frame. */
data class FloatingPhysicalDisplayBounds(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
)

data class FloatingBallPosition(
    val x: Int,
    val y: Int
)

data class FloatingMovementBounds(
    val freeLeft: Int,
    val freeTop: Int,
    val freeRight: Int,
    val freeBottom: Int,
    val dockedLeft: Int,
    val dockedRight: Int
)

data class FloatingDockResult(
    val position: FloatingBallPosition,
    val dockState: FloatingDockState?
)

class FloatingDockManager {

    fun createBounds(
        physicalDisplayBounds: FloatingPhysicalDisplayBounds,
        ballWidthPx: Int,
        ballHeightPx: Int
    ): FloatingMovementBounds {
        val freeRight = (physicalDisplayBounds.right - ballWidthPx)
            .coerceAtLeast(physicalDisplayBounds.left)
        val freeBottom = (physicalDisplayBounds.bottom - ballHeightPx)
            .coerceAtLeast(physicalDisplayBounds.top)
        return FloatingMovementBounds(
            freeLeft = physicalDisplayBounds.left,
            freeTop = physicalDisplayBounds.top,
            freeRight = freeRight,
            freeBottom = freeBottom,
            dockedLeft = physicalDisplayBounds.left,
            dockedRight = freeRight
        )
    }

    fun clampToFree(
        bounds: FloatingMovementBounds,
        x: Int,
        y: Int
    ): FloatingBallPosition {
        return FloatingBallPosition(
            x = x.coerceIn(bounds.freeLeft, bounds.freeRight),
            y = y.coerceIn(bounds.freeTop, bounds.freeBottom)
        )
    }

    fun resolveDocked(
        bounds: FloatingMovementBounds,
        config: FloatingDockConfig,
        dockState: FloatingDockState
    ): FloatingDockResult? {
        val normalizedState = dockState.normalized(config) ?: return null
        val position = resolveDockedPosition(bounds, normalizedState) ?: return null
        return FloatingDockResult(position = position, dockState = normalizedState)
    }

    fun resolveFreeRestingState(
        bounds: FloatingMovementBounds,
        x: Int,
        y: Int
    ): FloatingDockResult {
        val clamped = clampToFree(bounds, x, y)
        return FloatingDockResult(position = clamped, dockState = null)
    }

    fun resolveAnchoredFreePosition(
        previousBounds: FloatingMovementBounds,
        newBounds: FloatingMovementBounds,
        x: Int,
        y: Int
    ): FloatingBallPosition? {
        val clamped = clampToFree(previousBounds, x, y)
        val anchoredEdge = when (clamped.x) {
            previousBounds.freeLeft -> FloatingDockEdge.LEFT
            previousBounds.freeRight -> FloatingDockEdge.RIGHT
            else -> null
        } ?: return null
        val mappedY = lerp(
            newBounds.freeTop,
            newBounds.freeBottom,
            crossAxisPercent(previousBounds, clamped.y)
        )
        val mappedX = when (anchoredEdge) {
            FloatingDockEdge.LEFT -> newBounds.freeLeft
            FloatingDockEdge.RIGHT -> newBounds.freeRight
            else -> return null
        }
        return FloatingBallPosition(x = mappedX, y = mappedY)
    }

    private fun resolveDockedPosition(
        bounds: FloatingMovementBounds,
        dockState: FloatingDockState
    ): FloatingBallPosition? {
        val y = lerp(bounds.freeTop, bounds.freeBottom, dockState.crossAxisPercent)
        val x = when (dockState.dockedEdge) {
            FloatingDockEdge.LEFT -> bounds.dockedLeft
            FloatingDockEdge.RIGHT -> bounds.dockedRight
            else -> return null
        }
        return FloatingBallPosition(x = x, y = y)
    }

    private fun crossAxisPercent(bounds: FloatingMovementBounds, y: Int): Float {
        val clampedY = y.coerceIn(bounds.freeTop, bounds.freeBottom)
        val range = bounds.freeBottom - bounds.freeTop
        if (range <= 0) return 0.5f
        return (clampedY - bounds.freeTop).toFloat() / range.toFloat()
    }

    private fun lerp(start: Int, end: Int, fraction: Float): Int {
        if (start == end) return start
        return (start + (end - start) * fraction.coerceIn(0f, 1f)).roundToInt()
    }
}
