package com.chen.memorizewords.data.floating.repository

import com.chen.memorizewords.domain.floating.model.FloatingWordSettings
import kotlin.test.Test
import kotlin.test.assertEquals

class FloatingWordSettingsNormalizationTest {

    @Test
    fun `card gap defaults to eighteen dp`() {
        val settings = normalizeFloatingWordSettings(FloatingWordSettings())

        assertEquals(DEFAULT_CARD_GAP_DP, settings.cardGapDp)
    }

    @Test
    fun `card gap is clamped to supported range`() {
        val tooSmall = normalizeFloatingWordSettings(
            FloatingWordSettings(cardGapDp = MIN_CARD_GAP_DP - 1)
        )
        val tooLarge = normalizeFloatingWordSettings(
            FloatingWordSettings(cardGapDp = MAX_CARD_GAP_DP + 1)
        )

        assertEquals(MIN_CARD_GAP_DP, tooSmall.cardGapDp)
        assertEquals(MAX_CARD_GAP_DP, tooLarge.cardGapDp)
    }
}
