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

        assertEquals(petCenterX(80, 156), first.cardX + first.tailCenterX)
        assertEquals(petCenterX(110, 156), second.cardX + second.tailCenterX)
        assertEquals(30, second.cardX + second.tailCenterX - (first.cardX + first.tailCenterX))
    }

    @Test
    fun `card stays above pet window with configured gap`() {
        val layout = resolveForPet(x = 80, y = 430)

        assertEquals(FloatingSpeechPlacement.ABOVE_PET, layout.placement)
        assertEquals(430 - config.clearancePx, panelBottom(layout))
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
            60 + 187 + config.clearancePx,
            panelTop(layout)
        )
    }

    @Test
    fun `card y is stable across repeated openings at same pet position`() {
        val first = resolveForPet(x = 80, y = 430)
        val second = resolveForPet(x = 80, y = 430)

        assertEquals(FloatingSpeechPlacement.ABOVE_PET, first.placement)
        assertEquals(first.cardY, second.cardY)
        assertEquals(panelBottom(first), panelBottom(second))
    }

    @Test
    fun `card y is stable after switching from below placement back to above`() {
        val below = resolveForPet(x = 80, y = 60)
        val above = resolveForPet(x = 80, y = 430)
        val aboveAgain = resolveForPet(x = 80, y = 430)

        assertEquals(FloatingSpeechPlacement.BELOW_PET, below.placement)
        assertEquals(FloatingSpeechPlacement.ABOVE_PET, above.placement)
        assertEquals(above.cardY, aboveAgain.cardY)
        assertEquals(panelBottom(above), panelBottom(aboveAgain))
    }

    @Test
    fun `panel spacing is stable above pet when card height changes`() {
        val shortCardHeight = 220
        val tallCardHeight = 300
        val shortCard = resolveForPet(x = 80, y = 430, cardHeight = shortCardHeight)
        val tallCard = resolveForPet(x = 80, y = 430, cardHeight = tallCardHeight)

        assertEquals(FloatingSpeechPlacement.ABOVE_PET, shortCard.placement)
        assertEquals(FloatingSpeechPlacement.ABOVE_PET, tallCard.placement)
        assertEquals(430 - config.clearancePx, panelBottom(shortCard, shortCardHeight))
        assertEquals(430 - config.clearancePx, panelBottom(tallCard, tallCardHeight))
    }

    @Test
    fun `locked above placement keeps panel gap through every animation height`() {
        listOf(40, 0, -20).forEach { gap ->
            val gapConfig = config.copy(clearancePx = gap)
            listOf(300, 280, 260, 240, 220).forEach { height ->
                val layout = engine.resolveAnchored(
                    safeArea = safeArea,
                    petBounds = FloatingSpeechPetBounds(80, 430, 156, 187),
                    cardSize = FloatingSpeechCardSize(cardSize.width, height),
                    config = gapConfig,
                    placement = FloatingSpeechPlacement.ABOVE_PET
                )

                assertEquals(430 - gap, layout.cardY + height - config.tailSlotHeightPx)
            }
        }
    }

    @Test
    fun `locked below placement keeps panel gap through every animation height`() {
        listOf(40, 0, -20).forEach { gap ->
            val gapConfig = config.copy(clearancePx = gap)
            listOf(220, 240, 260, 280, 300).forEach { height ->
                val layout = engine.resolveAnchored(
                    safeArea = safeArea,
                    petBounds = FloatingSpeechPetBounds(80, 60, 156, 187),
                    cardSize = FloatingSpeechCardSize(cardSize.width, height),
                    config = gapConfig,
                    placement = FloatingSpeechPlacement.BELOW_PET
                )

                assertEquals(60 + 187 + gap, layout.cardY + config.tailSlotHeightPx)
            }
        }
    }

    @Test
    fun `available height prevents anchored animation from reaching a safe edge clamp`() {
        val pet = FloatingSpeechPetBounds(80, 430, 156, 187)
        val aboveAvailable = engine.availableHeight(
            safeArea,
            pet,
            config,
            FloatingSpeechPlacement.ABOVE_PET
        )
        val belowAvailable = engine.availableHeight(
            safeArea,
            pet,
            config,
            FloatingSpeechPlacement.BELOW_PET
        )

        assertEquals(430 - config.clearancePx + config.tailSlotHeightPx - 36, aboveAvailable)
        assertEquals(748 - (430 + 187 + config.clearancePx - config.tailSlotHeightPx), belowAvailable)
    }

    @Test
    fun `placement locks below only when the minimum card cannot fit above`() {
        assertEquals(
            FloatingSpeechPlacement.ABOVE_PET,
            engine.resolvePlacementForMinimumHeight(
                safeArea,
                FloatingSpeechPetBounds(80, 430, 156, 187),
                minimumCardHeightPx = 192,
                config = config
            )
        )
        assertEquals(
            FloatingSpeechPlacement.BELOW_PET,
            engine.resolvePlacementForMinimumHeight(
                safeArea,
                FloatingSpeechPetBounds(80, 60, 156, 187),
                minimumCardHeightPx = 192,
                config = config
            )
        )
    }

    @Test
    fun `card width uses the preferred width until the safe area becomes narrow`() {
        assertEquals(320, resolveFloatingCardWidth(safeArea, 320, 12))
        assertEquals(
            216,
            resolveFloatingCardWidth(
                safeArea.copy(right = 240),
                preferredWidthPx = 320,
                edgeMarginPx = 12
            )
        )
    }

    @Test
    fun `natural card height is capped by aspect ratio and anchored side`() {
        assertEquals(
            360,
            resolveFloatingCardTargetHeight(
                naturalHeightPx = 600,
                minimumHeightPx = 192,
                cardWidthPx = 320,
                placementAvailableHeightPx = 360
            )
        )
        assertEquals(
            480,
            resolveFloatingCardTargetHeight(
                naturalHeightPx = 600,
                minimumHeightPx = 192,
                cardWidthPx = 320,
                placementAvailableHeightPx = 700
            )
        )
    }

    @Test
    fun `minimum card height is soft when the anchored side is smaller`() {
        assertEquals(
            150,
            resolveFloatingCardTargetHeight(
                naturalHeightPx = 300,
                minimumHeightPx = 192,
                cardWidthPx = 320,
                placementAvailableHeightPx = 150
            )
        )
    }

    @Test
    fun `scaled pet size changes the pet center used by the card and tail`() {
        val normal = resolveForPet(x = 80, y = 430, width = 156, height = 187)
        val scaled = resolveForPet(x = 80, y = 430, width = 203, height = 243)

        assertEquals(petCenterX(80, 156), normal.cardX + normal.tailCenterX)
        assertEquals(petCenterX(80, 203), scaled.cardX + scaled.tailCenterX)
        assertEquals(
            430 - config.clearancePx,
            panelBottom(scaled)
        )
    }

    @Test
    fun `negative gap overlaps panel edge with pet window edge`() {
        val negativeGapConfig = config.copy(clearancePx = -50)
        val layout = engine.resolve(
            safeArea = safeArea,
            petBounds = FloatingSpeechPetBounds(x = 80, y = 430, width = 156, height = 187),
            cardSize = cardSize,
            config = negativeGapConfig
        )

        assertEquals(FloatingSpeechPlacement.ABOVE_PET, layout.placement)
        assertEquals(430 - negativeGapConfig.clearancePx, panelBottom(layout))
    }

    @Test
    fun `card x centers on pet window before edge clamp`() {
        val layout = resolveForPet(x = 80, y = 430)

        assertEquals(80 + 156 / 2 - cardSize.width / 2, layout.cardX)
    }

    @Test
    fun `card y below pet clamps to bottom safe edge`() {
        val layout = resolveForPet(x = 80, y = 300)

        assertEquals(FloatingSpeechPlacement.BELOW_PET, layout.placement)
        assertEquals(safeArea.bottom - cardSize.height - config.edgeMarginPx, layout.cardY)
    }

    @Test
    fun `saved pet coordinates are resolved before first card layout`() {
        val bounds = FloatingMovementBounds(
            freeLeft = 0,
            freeTop = 24,
            freeRight = 240,
            freeBottom = 520,
            dockedLeft = -120,
            dockedRight = 320,
            visibleWidth = 40,
            hiddenWidth = 116
        )
        val position = resolveBallPositionForSettings(
            preferences = com.chen.memorizewords.domain.floating.model.FloatingDevicePreferences(
                floatingBallX = 90,
                floatingBallY = 410
            ),
            bounds = bounds,
            previousBounds = null
        )
        val layout = resolveForPet(x = position.x, y = position.y)

        assertEquals(FloatingBallPosition(x = 90, y = 410), position)
        assertEquals(410 - config.clearancePx, panelBottom(layout))
    }

    private fun resolveForPet(
        x: Int,
        y: Int,
        width: Int = 156,
        height: Int = 187,
        cardHeight: Int = cardSize.height
    ): FloatingSpeechLayout {
        return engine.resolve(
            safeArea = safeArea,
            petBounds = FloatingSpeechPetBounds(x = x, y = y, width = width, height = height),
            cardSize = cardSize.copy(height = cardHeight),
            config = config
        )
    }

    private fun panelBottom(
        layout: FloatingSpeechLayout,
        cardHeight: Int = cardSize.height
    ): Int = layout.cardY + cardHeight - config.tailSlotHeightPx

    private fun panelTop(layout: FloatingSpeechLayout): Int {
        return layout.cardY + config.tailSlotHeightPx
    }

    private fun petCenterX(x: Int, width: Int): Int {
        return (x + width / 2f).roundToInt()
    }
}
