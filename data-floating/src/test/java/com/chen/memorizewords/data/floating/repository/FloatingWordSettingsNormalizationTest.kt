package com.chen.memorizewords.data.floating.repository

import com.chen.memorizewords.domain.floating.model.FloatingWordSettings
import kotlin.test.Test
import kotlin.test.assertEquals

class FloatingWordSettingsNormalizationTest {

    @Test
    fun `appearance settings default to supported values`() {
        val settings = normalizeFloatingWordSettings(FloatingWordSettings())

        assertEquals(DEFAULT_BALL_SIZE_PERCENT, settings.ballSizePercent)
        assertEquals(DEFAULT_CARD_GAP_DP, settings.cardGapDp)
    }

    @Test
    fun `ball size is clamped to supported range`() {
        val tooSmall = normalizeFloatingWordSettings(
            FloatingWordSettings(ballSizePercent = MIN_BALL_SIZE_PERCENT - 1)
        )
        val tooLarge = normalizeFloatingWordSettings(
            FloatingWordSettings(ballSizePercent = MAX_BALL_SIZE_PERCENT + 1)
        )

        assertEquals(MIN_BALL_SIZE_PERCENT, tooSmall.ballSizePercent)
        assertEquals(MAX_BALL_SIZE_PERCENT, tooLarge.ballSizePercent)
    }

    @Test
    fun `card gap is clamped to supported range`() {
        val tooSmall = normalizeFloatingWordSettings(
            FloatingWordSettings(cardGapDp = MIN_CARD_GAP_DP - 1)
        )
        val negativeInRange = normalizeFloatingWordSettings(
            FloatingWordSettings(cardGapDp = -60)
        )
        val tooLarge = normalizeFloatingWordSettings(
            FloatingWordSettings(cardGapDp = MAX_CARD_GAP_DP + 1)
        )

        assertEquals(MIN_CARD_GAP_DP, tooSmall.cardGapDp)
        assertEquals(-60, negativeInRange.cardGapDp)
        assertEquals(MAX_CARD_GAP_DP, tooLarge.cardGapDp)
    }

    @Test
    fun `unsafe character pack id falls back to bundled pack`() {
        val settings = normalizeFloatingWordSettings(
            FloatingWordSettings(selectedCharacterPackId = "../unsafe")
        )

        assertEquals(FloatingWordSettings.DEFAULT_CHARACTER_PACK_ID, settings.selectedCharacterPackId)
    }
}
