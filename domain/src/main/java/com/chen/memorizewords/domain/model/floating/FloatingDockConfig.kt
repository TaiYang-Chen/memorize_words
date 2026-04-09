package com.chen.memorizewords.domain.model.floating

data class FloatingDockConfig(
    val snapTriggerDistanceDp: Int = 24,
    val halfHiddenEnabled: Boolean = true,
    val allowedEdges: List<FloatingDockEdge> = defaultAllowedEdges(),
    val edgePriority: List<FloatingDockEdge> = defaultAllowedEdges(),
    val snapAnimationDurationMs: Long = 220L,
    val tapExpandsCardAfterUnsnap: Boolean = false,
    val initialDockEdge: FloatingDockEdge = FloatingDockEdge.RIGHT
) {
    fun normalized(): FloatingDockConfig {
        val allowed = (allowedEdges as? List<*>)?.filterIsInstance<FloatingDockEdge>()
            .orEmpty()
            .filterSupported()
            .ifEmpty { defaultAllowedEdges() }
        val priority = (edgePriority as? List<*>)?.filterIsInstance<FloatingDockEdge>()
            .orEmpty()
            .filterSupported()
            .filter { it in allowed }
            .ifEmpty { allowed }
        val initial = (initialDockEdge as? FloatingDockEdge)
            ?.takeIf { it in allowed && it.isSupportedDockEdge() }
            ?: priority.firstOrNull()
            ?: FloatingDockEdge.RIGHT
        return copy(
            snapTriggerDistanceDp = snapTriggerDistanceDp.coerceAtLeast(0),
            allowedEdges = allowed,
            edgePriority = priority,
            snapAnimationDurationMs = snapAnimationDurationMs.coerceAtLeast(0L),
            initialDockEdge = initial
        )
    }

    companion object {
        fun defaultAllowedEdges(): List<FloatingDockEdge> {
            return listOf(FloatingDockEdge.LEFT, FloatingDockEdge.RIGHT)
        }
    }
}

internal fun FloatingDockEdge.isSupportedDockEdge(): Boolean {
    return this == FloatingDockEdge.LEFT || this == FloatingDockEdge.RIGHT
}

internal fun List<FloatingDockEdge>.filterSupported(): List<FloatingDockEdge> {
    return distinct().filter(FloatingDockEdge::isSupportedDockEdge)
}
