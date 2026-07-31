package com.chen.memorizewords.feature.floatingreview.ui.floating

import kotlin.math.roundToInt

internal const val FLOATING_CARD_MAX_HEIGHT_TO_WIDTH_RATIO = 1.5f

internal fun resolveFloatingCardWidth(
    safeArea: FloatingSpeechSafeArea,
    preferredWidthPx: Int,
    edgeMarginPx: Int
): Int {
    val availableWidth = safeArea.right - safeArea.left - edgeMarginPx * 2
    return minOf(preferredWidthPx, availableWidth).coerceAtLeast(1)
}

internal fun resolveFloatingCardTargetHeight(
    naturalHeightPx: Int,
    minimumHeightPx: Int,
    cardWidthPx: Int,
    placementAvailableHeightPx: Int
): Int {
    val aspectHeight = (cardWidthPx * FLOATING_CARD_MAX_HEIGHT_TO_WIDTH_RATIO).roundToInt()
    val maximumHeight = minOf(aspectHeight, placementAvailableHeightPx).coerceAtLeast(1)
    if (maximumHeight < minimumHeightPx) return maximumHeight
    return naturalHeightPx.coerceIn(minimumHeightPx, maximumHeight)
}

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

    fun resolvePlacementForMinimumHeight(
        safeArea: FloatingSpeechSafeArea,
        petBounds: FloatingSpeechPetBounds,
        minimumCardHeightPx: Int,
        config: FloatingSpeechLayoutConfig
    ): FloatingSpeechPlacement {
        val aboveHeight = availableHeight(
            safeArea = safeArea,
            petBounds = petBounds,
            config = config,
            placement = FloatingSpeechPlacement.ABOVE_PET
        )
        val belowHeight = availableHeight(
            safeArea = safeArea,
            petBounds = petBounds,
            config = config,
            placement = FloatingSpeechPlacement.BELOW_PET
        )
        return when {
            aboveHeight >= minimumCardHeightPx -> FloatingSpeechPlacement.ABOVE_PET
            belowHeight >= minimumCardHeightPx -> FloatingSpeechPlacement.BELOW_PET
            aboveHeight >= belowHeight -> FloatingSpeechPlacement.ABOVE_PET
            else -> FloatingSpeechPlacement.BELOW_PET
        }
    }

    fun availableHeight(
        safeArea: FloatingSpeechSafeArea,
        petBounds: FloatingSpeechPetBounds,
        config: FloatingSpeechLayoutConfig,
        placement: FloatingSpeechPlacement
    ): Int {
        val minY = safeArea.top + config.edgeMarginPx
        val maxBottom = safeArea.bottom - config.edgeMarginPx
        return when (placement) {
            FloatingSpeechPlacement.ABOVE_PET ->
                petBounds.y - config.clearancePx + config.tailSlotHeightPx - minY

            FloatingSpeechPlacement.BELOW_PET ->
                maxBottom - (
                    petBounds.y + petBounds.height + config.clearancePx - config.tailSlotHeightPx
                    )
        }.coerceAtLeast(0)
    }

    fun resolveAnchored(
        safeArea: FloatingSpeechSafeArea,
        petBounds: FloatingSpeechPetBounds,
        cardSize: FloatingSpeechCardSize,
        config: FloatingSpeechLayoutConfig,
        placement: FloatingSpeechPlacement
    ): FloatingSpeechLayout {
        val anchorX = (petBounds.x + petBounds.width / 2f).roundToInt()
        val cardX = resolveCardX(safeArea, cardSize, config, anchorX)
        val cardY = when (placement) {
            FloatingSpeechPlacement.ABOVE_PET -> aboveCardY(cardSize, config, petBounds)
            FloatingSpeechPlacement.BELOW_PET ->
                petBounds.y + petBounds.height + config.clearancePx - config.tailSlotHeightPx
        }
        return FloatingSpeechLayout(
            cardX = cardX,
            cardY = cardY,
            tailCenterX = resolveTailCenterX(cardSize, config, anchorX - cardX),
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
