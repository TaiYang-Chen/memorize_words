package com.chen.memorizewords.domain.model.floating

data class FloatingDockState(
    val dockedEdge: FloatingDockEdge? = null,
    val crossAxisPercent: Float = 0.5f
) {
    fun normalized(config: FloatingDockConfig): FloatingDockState? {
        val normalizedConfig = config.normalized()
        val edge = dockedEdge
            ?.takeIf { it.isSupportedDockEdge() && it in normalizedConfig.allowedEdges }
            ?: return null
        return copy(
            dockedEdge = edge,
            crossAxisPercent = crossAxisPercent.coerceIn(0f, 1f)
        )
    }
}
