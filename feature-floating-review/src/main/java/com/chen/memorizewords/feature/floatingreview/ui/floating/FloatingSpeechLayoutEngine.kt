package com.chen.memorizewords.feature.floatingreview.ui.floating

import kotlin.math.roundToInt

data class FloatingSpeechSafeArea(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
)

data class FloatingSpeechPetBounds(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int
)

data class FloatingSpeechPetVisualBounds(
    val leftPercent: Float = 0.42f,
    val topPercent: Float = 0.46f,
    val rightPercent: Float = 0.96f,
    val bottomPercent: Float = 0.96f
)

data class FloatingSpeechCardSize(
    val width: Int,
    val height: Int
)

data class FloatingSpeechLayoutConfig(
    val edgeMarginPx: Int,
    val clearancePx: Int,
    val tailWidthPx: Int,
    val tailSafeInsetPx: Int,
    val tailSlotHeightPx: Int,
    val petVisualBounds: FloatingSpeechPetVisualBounds = FloatingSpeechPetVisualBounds()
)

data class FloatingSpeechLayout(
    val cardX: Int,
    val cardY: Int,
    val tailCenterX: Int,
    val placement: FloatingSpeechPlacement
)

enum class FloatingSpeechPlacement {
    ABOVE_PET,
    BELOW_PET
}

class FloatingSpeechLayoutEngine {

    fun resolve(
        safeArea: FloatingSpeechSafeArea,
        petBounds: FloatingSpeechPetBounds,
        cardSize: FloatingSpeechCardSize,
        config: FloatingSpeechLayoutConfig
    ): FloatingSpeechLayout {
        val visibleBounds = resolveVisibleBounds(petBounds, config.petVisualBounds)
        val anchorX = (visibleBounds.left + (visibleBounds.right - visibleBounds.left) / 2f)
            .roundToInt()
        val cardX = resolveCardX(safeArea, cardSize, config, anchorX)
        val placement = resolvePlacement(safeArea, cardSize, config, visibleBounds)
        val cardY = resolveCardY(safeArea, cardSize, config, visibleBounds, placement)
        val tailCenterX = resolveTailCenterX(cardSize, config, anchorX - cardX)
        return FloatingSpeechLayout(
            cardX = cardX,
            cardY = cardY,
            tailCenterX = tailCenterX,
            placement = placement
        )
    }

    private fun resolveVisibleBounds(
        petBounds: FloatingSpeechPetBounds,
        visualBounds: FloatingSpeechPetVisualBounds
    ): FloatingSpeechResolvedPetBounds {
        val left = petBounds.x + petBounds.width * visualBounds.leftPercent.coerceIn(0f, 1f)
        val top = petBounds.y + petBounds.height * visualBounds.topPercent.coerceIn(0f, 1f)
        val right = petBounds.x + petBounds.width * visualBounds.rightPercent.coerceIn(0f, 1f)
        val bottom = petBounds.y + petBounds.height * visualBounds.bottomPercent.coerceIn(0f, 1f)
        return FloatingSpeechResolvedPetBounds(
            left = left.roundToInt(),
            top = top.roundToInt(),
            right = right.roundToInt(),
            bottom = bottom.roundToInt()
        )
    }

    private fun resolveCardX(
        safeArea: FloatingSpeechSafeArea,
        cardSize: FloatingSpeechCardSize,
        config: FloatingSpeechLayoutConfig,
        anchorX: Int
    ): Int {
        val minX = safeArea.left + config.edgeMarginPx
        val maxX = (safeArea.right - cardSize.width - config.edgeMarginPx).coerceAtLeast(minX)
        return (anchorX - cardSize.width / 2).coerceIn(minX, maxX)
    }

    private fun resolvePlacement(
        safeArea: FloatingSpeechSafeArea,
        cardSize: FloatingSpeechCardSize,
        config: FloatingSpeechLayoutConfig,
        visibleBounds: FloatingSpeechResolvedPetBounds
    ): FloatingSpeechPlacement {
        val minY = safeArea.top + config.edgeMarginPx
        val panelHeight = resolvePanelHeight(cardSize, config)
        return if (visibleBounds.top - panelHeight - config.clearancePx >= minY) {
            FloatingSpeechPlacement.ABOVE_PET
        } else {
            FloatingSpeechPlacement.BELOW_PET
        }
    }

    private fun resolveCardY(
        safeArea: FloatingSpeechSafeArea,
        cardSize: FloatingSpeechCardSize,
        config: FloatingSpeechLayoutConfig,
        visibleBounds: FloatingSpeechResolvedPetBounds,
        placement: FloatingSpeechPlacement
    ): Int {
        val minY = safeArea.top + config.edgeMarginPx
        val maxY = (safeArea.bottom - cardSize.height - config.edgeMarginPx).coerceAtLeast(minY)
        val panelHeight = resolvePanelHeight(cardSize, config)
        val desiredY = when (placement) {
            FloatingSpeechPlacement.ABOVE_PET ->
                visibleBounds.top - panelHeight - config.clearancePx

            FloatingSpeechPlacement.BELOW_PET ->
                visibleBounds.bottom + config.clearancePx - config.tailSlotHeightPx
        }
        return desiredY.coerceIn(minY, maxY)
    }

    private fun resolvePanelHeight(
        cardSize: FloatingSpeechCardSize,
        config: FloatingSpeechLayoutConfig
    ): Int {
        return (cardSize.height - config.tailSlotHeightPx).coerceAtLeast(0)
    }

    private fun resolveTailCenterX(
        cardSize: FloatingSpeechCardSize,
        config: FloatingSpeechLayoutConfig,
        desiredCenterX: Int
    ): Int {
        val halfTail = config.tailWidthPx / 2
        val minCenter = config.tailSafeInsetPx + halfTail
        val maxCenter = (cardSize.width - config.tailSafeInsetPx - halfTail).coerceAtLeast(minCenter)
        return desiredCenterX.coerceIn(minCenter, maxCenter)
    }

    private data class FloatingSpeechResolvedPetBounds(
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int
    )
}
