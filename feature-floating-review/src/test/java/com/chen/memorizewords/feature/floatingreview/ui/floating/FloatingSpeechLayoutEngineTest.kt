package com.chen.memorizewords.feature.floatingreview.ui.floating

import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FloatingSpeechLayoutEngineTest {

    private val engine = FloatingSpeechLayoutEngine()
    private val safeArea = FloatingSpeechSafeArea(left = 0, top = 24, right = 360, bottom = 760)
    private val cardSize = FloatingSpeechCardSize(width = 242, height = 260)
    private val config = FloatingSpeechLayoutConfig(
        edgeMarginPx = 12,
        clearancePx = 40,
        tailWidthPx = 34,
        tailSafeInsetPx = 24,
        tailSlotHeightPx = 18
    )

    @Test
    fun `tail follows pet anchor while pet moves horizontally`() {
        val first = resolveForPet(x = 80, y = 430)
        val second = resolveForPet(x = 110, y = 430)

        assertEquals(visibleCenterX(80, 156), first.cardX + first.tailCenterX)
        assertEquals(visibleCenterX(110, 156), second.cardX + second.tailCenterX)
        assertEquals(30, second.cardX + second.tailCenterX - (first.cardX + first.tailCenterX))
    }

    @Test
    fun `card panel stays above pet visible bounds with clear spacing`() {
        val layout = resolveForPet(x = 80, y = 430)

        assertEquals(FloatingSpeechPlacement.ABOVE_PET, layout.placement)
        assertEquals(visibleTop(430, 187) - config.clearancePx, panelBottom(layout))
    }

    @Test
    fun `tail is constrained inside card when card is clamped to right edge`() {
        val layout = resolveForPet(x = 280, y = 430)
        val minTailCenter = config.tailSafeInsetPx + config.tailWidthPx / 2
        val maxTailCenter = cardSize.width - config.tailSafeInsetPx - config.tailWidthPx / 2

        assertEquals(safeArea.right - cardSize.width - config.edgeMarginPx, layout.cardX)
        assertTrue(layout.tailCenterX in minTailCenter..maxTailCenter)
        assertEquals(maxTailCenter, layout.tailCenterX)
        assertTrue(cardSize.width / 2 < layout.tailCenterX)
    }

    @Test
    fun `card moves below pet when there is not enough room above`() {
        val layout = resolveForPet(x = 80, y = 60)

        assertEquals(FloatingSpeechPlacement.BELOW_PET, layout.placement)
        assertEquals(
            visibleBottom(60, 187) + config.clearancePx,
            panelTop(layout)
        )
    }

    @Test
    fun `scaled pet size changes the visual anchor used by the card and tail`() {
        val normal = resolveForPet(x = 80, y = 430, width = 156, height = 187)
        val scaled = resolveForPet(x = 80, y = 430, width = 203, height = 243)

        assertEquals(visibleCenterX(80, 156), normal.cardX + normal.tailCenterX)
        assertEquals(visibleCenterX(80, 203), scaled.cardX + scaled.tailCenterX)
        assertEquals(
            visibleTop(430, 243) - config.clearancePx,
            panelBottom(scaled)
        )
    }

    private fun resolveForPet(
        x: Int,
        y: Int,
        width: Int = 156,
        height: Int = 187
    ): FloatingSpeechLayout {
        return engine.resolve(
            safeArea = safeArea,
            petBounds = FloatingSpeechPetBounds(x = x, y = y, width = width, height = height),
            cardSize = cardSize,
            config = config
        )
    }

    private fun panelTop(layout: FloatingSpeechLayout): Int {
        return when (layout.placement) {
            FloatingSpeechPlacement.ABOVE_PET -> layout.cardY
            FloatingSpeechPlacement.BELOW_PET -> layout.cardY + config.tailSlotHeightPx
        }
    }

    private fun panelBottom(layout: FloatingSpeechLayout): Int {
        return panelTop(layout) + panelHeight()
    }

    private fun panelHeight(): Int {
        return cardSize.height - config.tailSlotHeightPx
    }

    private fun visibleCenterX(x: Int, width: Int): Int {
        val visual = config.petVisualBounds
        val left = x + width * visual.leftPercent
        val right = x + width * visual.rightPercent
        return (left + (right - left) / 2f).roundToInt()
    }

    private fun visibleTop(y: Int, height: Int): Int {
        return (y + height * config.petVisualBounds.topPercent).roundToInt()
    }

    private fun visibleBottom(y: Int, height: Int): Int {
        return (y + height * config.petVisualBounds.bottomPercent).roundToInt()
    }
}
