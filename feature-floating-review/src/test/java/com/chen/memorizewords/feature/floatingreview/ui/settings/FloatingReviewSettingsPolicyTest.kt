package com.chen.memorizewords.feature.floatingreview.ui.settings

import com.chen.memorizewords.domain.floating.model.FloatingWordOrderType
import com.chen.memorizewords.domain.floating.model.FloatingWordSettings
import com.chen.memorizewords.domain.floating.model.FloatingWordSourceType
import com.chen.memorizewords.feature.floatingreview.ui.floating.resolveFloatingSettingsChange
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FloatingReviewSettingsPolicyTest {

    @Test
    fun `card appearance changes stay in the card surface domain`() {
        val previous = FloatingWordSettings(cardOpacityPercent = 100, cardGapDp = 40)
        val updated = previous.copy(cardOpacityPercent = 70, cardGapDp = 24)

        val change = resolveFloatingSettingsChange(previous, updated)

        assertTrue(change.cardOpacityChanged)
        assertTrue(change.cardGapChanged)
        assertFalse(change.wordSequenceChanged)
        assertFalse(change.ballSizeChanged)
        assertFalse(change.characterPackChanged)
    }

    @Test
    fun `word source changes invalidate only the card sequence`() {
        val previous = FloatingWordSettings(
            sourceType = FloatingWordSourceType.SELF_SELECT,
            orderType = FloatingWordOrderType.RANDOM,
            selectedWordIds = listOf(1L)
        )
        val updated = previous.copy(
            sourceType = FloatingWordSourceType.CURRENT_BOOK,
            orderType = FloatingWordOrderType.MEMORY_CURVE,
            selectedWordIds = listOf(2L)
        )

        val change = resolveFloatingSettingsChange(previous, updated)

        assertTrue(change.wordSequenceChanged)
        assertFalse(change.cardOpacityChanged)
        assertFalse(change.characterPackChanged)
        assertFalse(change.ballPositionChanged)
    }

    @Test
    fun `field configuration changes stay separate from word selection`() {
        val previous = FloatingWordSettings()
        val updated = previous.copy(
            fieldConfigs = previous.fieldConfigs.mapIndexed { index, config ->
                if (index == 0) config.copy(enabled = !config.enabled) else config
            }
        )

        val change = resolveFloatingSettingsChange(previous, updated)

        assertTrue(change.fieldConfigsChanged)
        assertFalse(change.wordSequenceChanged)
        assertFalse(change.ballSizeChanged)
        assertFalse(change.cardGapChanged)
    }

    @Test
    fun `ball position and auto start do not change card content responsibilities`() {
        val previous = FloatingWordSettings()
        val updated = previous.copy(
            autoStartOnAppLaunch = true,
            ballSizePercent = 75,
            ballOpacityPercent = 80,
            floatingBallX = 24,
            floatingBallY = 48
        )

        val change = resolveFloatingSettingsChange(previous, updated)

        assertTrue(change.autoStartChanged)
        assertTrue(change.ballSizeChanged)
        assertTrue(change.ballOpacityChanged)
        assertTrue(change.ballPositionChanged)
        assertFalse(change.wordSequenceChanged)
        assertFalse(change.fieldConfigsChanged)
    }
}
