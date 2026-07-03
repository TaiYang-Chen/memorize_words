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

data class FloatingSpeechCardSize(
    val width: Int,
    val height: Int
)

data class FloatingSpeechLayoutConfig(
    val edgeMarginPx: Int,
    val clearancePx: Int,
    val tailWidthPx: Int,
    val tailSafeInsetPx: Int,
    val tailSlotHeightPx: Int
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
        val anchorX = (petBounds.x + petBounds.width / 2f).roundToInt()
        val cardX = resolveCardX(safeArea, cardSize, config, anchorX)
        val placement = resolvePlacement(safeArea, cardSize, config, petBounds)
        val cardY = resolveCardY(safeArea, cardSize, config, petBounds, placement)
        val tailCenterX = resolveTailCenterX(cardSize, config, anchorX - cardX)
        return FloatingSpeechLayout(
            cardX = cardX,
            cardY = cardY,
            tailCenterX = tailCenterX,
            placement = placement
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
        petBounds: FloatingSpeechPetBounds
    ): FloatingSpeechPlacement {
        val minY = safeArea.top + config.edgeMarginPx
        return if (aboveCardY(cardSize, config, petBounds) >= minY) {
            FloatingSpeechPlacement.ABOVE_PET
        } else {
            FloatingSpeechPlacement.BELOW_PET
        }
    }

    private fun resolveCardY(
        safeArea: FloatingSpeechSafeArea,
        cardSize: FloatingSpeechCardSize,
        config: FloatingSpeechLayoutConfig,
        petBounds: FloatingSpeechPetBounds,
        placement: FloatingSpeechPlacement
    ): Int {
        val minY = safeArea.top + config.edgeMarginPx
        val maxY = (safeArea.bottom - cardSize.height - config.edgeMarginPx).coerceAtLeast(minY)
        val desiredY = when (placement) {
            FloatingSpeechPlacement.ABOVE_PET -> aboveCardY(cardSize, config, petBounds)

            FloatingSpeechPlacement.BELOW_PET ->
                petBounds.y + petBounds.height + config.clearancePx - config.tailSlotHeightPx
        }
        return desiredY.coerceIn(minY, maxY)
    }

    private fun aboveCardY(
        cardSize: FloatingSpeechCardSize,
        config: FloatingSpeechLayoutConfig,
        petBounds: FloatingSpeechPetBounds
    ): Int {
        return petBounds.y - cardSize.height - config.clearancePx + config.tailSlotHeightPx
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
}
