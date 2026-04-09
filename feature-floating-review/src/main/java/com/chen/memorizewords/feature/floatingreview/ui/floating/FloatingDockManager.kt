package com.chen.memorizewords.feature.floatingreview.ui.floating

import com.chen.memorizewords.domain.model.floating.FloatingDockConfig
import com.chen.memorizewords.domain.model.floating.FloatingDockEdge
import com.chen.memorizewords.domain.model.floating.FloatingDockState
import kotlin.math.abs
import kotlin.math.roundToInt

data class FloatingAvailableArea(
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
    val dockedRight: Int,
    val visibleWidth: Int,
    val hiddenWidth: Int
)

data class FloatingDockResult(
    val position: FloatingBallPosition,
    val dockState: FloatingDockState?
)

class FloatingDockManager {

    fun createBounds(
        safeArea: FloatingAvailableArea,
        ballSizePx: Int,
        config: FloatingDockConfig
    ): FloatingMovementBounds {
        val normalizedConfig = config.normalized()
        val visibleWidth = resolveVisibleWidthPx(ballSizePx, normalizedConfig)
        val hiddenWidth = (ballSizePx - visibleWidth).coerceAtLeast(0)
        val freeRight = (safeArea.right - ballSizePx).coerceAtLeast(safeArea.left)
        val freeBottom = (safeArea.bottom - ballSizePx).coerceAtLeast(safeArea.top)
        return FloatingMovementBounds(
            freeLeft = safeArea.left,
            freeTop = safeArea.top,
            freeRight = freeRight,
            freeBottom = freeBottom,
            dockedLeft = safeArea.left - hiddenWidth,
            dockedRight = safeArea.right - visibleWidth,
            visibleWidth = visibleWidth,
            hiddenWidth = hiddenWidth
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

    fun resolveRestingState(
        bounds: FloatingMovementBounds,
        config: FloatingDockConfig,
        snapTriggerDistancePx: Int,
        x: Int,
        y: Int
    ): FloatingDockResult {
        val clamped = clampToFree(bounds, x, y)
        val edge = nearestDockEdge(bounds, config, clamped)
        if (edge != null && distanceToEdge(bounds, clamped, edge) <= snapTriggerDistancePx) {
            val dockState = FloatingDockState(
                dockedEdge = edge,
                crossAxisPercent = crossAxisPercent(bounds, clamped.y)
            )
            return resolveDocked(bounds, config, dockState)
                ?: FloatingDockResult(position = clamped, dockState = null)
        }
        return FloatingDockResult(position = clamped, dockState = null)
    }

    fun dockToNearestEdge(
        bounds: FloatingMovementBounds,
        config: FloatingDockConfig,
        x: Int,
        y: Int
    ): FloatingDockResult {
        val clamped = clampToFree(bounds, x, y)
        val edge = nearestDockEdge(bounds, config, clamped)
        if (edge == null) {
            return FloatingDockResult(position = clamped, dockState = null)
        }
        val dockState = FloatingDockState(
            dockedEdge = edge,
            crossAxisPercent = crossAxisPercent(bounds, clamped.y)
        )
        return resolveDocked(bounds, config, dockState)
            ?: FloatingDockResult(position = clamped, dockState = null)
    }

    fun buildInitialState(
        bounds: FloatingMovementBounds,
        config: FloatingDockConfig
    ): FloatingDockResult {
        val normalizedConfig = config.normalized()
        val state = FloatingDockState(
            dockedEdge = normalizedConfig.initialDockEdge,
            crossAxisPercent = 0.5f
        )
        return resolveDocked(bounds, normalizedConfig, state)
            ?: FloatingDockResult(
                position = clampToFree(bounds, bounds.freeRight, ((bounds.freeTop + bounds.freeBottom) / 2f).roundToInt()),
                dockState = null
            )
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

    fun revealDockedPosition(
        bounds: FloatingMovementBounds,
        config: FloatingDockConfig,
        dockState: FloatingDockState
    ): FloatingBallPosition? {
        val normalizedState = dockState.normalized(config) ?: return null
        val y = lerp(bounds.freeTop, bounds.freeBottom, normalizedState.crossAxisPercent)
        val x = when (normalizedState.dockedEdge) {
            FloatingDockEdge.LEFT -> bounds.freeLeft
            FloatingDockEdge.RIGHT -> bounds.freeRight
            else -> return null
        }
        return FloatingBallPosition(x = x, y = y)
    }

    fun resolveVisibleWidthPx(
        ballSizePx: Int,
        config: FloatingDockConfig
    ): Int {
        if (ballSizePx <= 0) return 0
        val normalizedConfig = config.normalized()
        if (!normalizedConfig.halfHiddenEnabled) return ballSizePx
        return (ballSizePx / 2f).roundToInt().coerceIn(1, ballSizePx)
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

    private fun nearestDockEdge(
        bounds: FloatingMovementBounds,
        config: FloatingDockConfig,
        position: FloatingBallPosition
    ): FloatingDockEdge? {
        val normalizedConfig = config.normalized()
        return normalizedConfig.edgePriority.minWithOrNull(
            compareBy<FloatingDockEdge> { distanceToEdge(bounds, position, it) }
                .thenBy { normalizedConfig.edgePriority.indexOf(it) }
        )
    }

    private fun distanceToEdge(
        bounds: FloatingMovementBounds,
        position: FloatingBallPosition,
        edge: FloatingDockEdge
    ): Int {
        return when (edge) {
            FloatingDockEdge.LEFT -> abs(position.x - bounds.freeLeft)
            FloatingDockEdge.RIGHT -> abs(bounds.freeRight - position.x)
            else -> Int.MAX_VALUE
        }
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
