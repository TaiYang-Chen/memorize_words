package com.chen.memorizewords.feature.floatingreview.ui.floating

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FloatingWordServiceAppearanceTest {

    @Test
    fun `ball size scale follows supported percentage range`() {
        assertEquals(0.01f, resolveBallSizeScale(1))
        assertEquals(0.6f, resolveBallSizeScale(60))
        assertEquals(2f, resolveBallSizeScale(200))
    }

    @Test
    fun `stale renderer operations are rejected after a replacement operation`() {
        assertTrue(
            isFloatingServiceOperationActive(
                stopping = false,
                currentGeneration = 4L,
                operationGeneration = 4L
            )
        )
        assertFalse(
            isFloatingServiceOperationActive(
                stopping = false,
                currentGeneration = 5L,
                operationGeneration = 4L
            )
        )
        assertFalse(
            isFloatingServiceOperationActive(
                stopping = true,
                currentGeneration = 4L,
                operationGeneration = 4L
            )
        )
    }

    @Test
    fun `character renderer reloads when first frame is not ready`() {
        assertFalse(shouldReloadFloatingCharacterPack(revisionMatches = true, packReady = true))
        assertTrue(shouldReloadFloatingCharacterPack(revisionMatches = false, packReady = true))
        assertTrue(shouldReloadFloatingCharacterPack(revisionMatches = true, packReady = false))
    }

    @Test
    fun `visible card reacts only to relevant presentation changes`() {
        assertEquals(
            FloatingCardSettingsAction.NONE,
            resolveFloatingCardSettingsAction(
                cardVisible = false,
                hasCurrentWord = true,
                wordSequenceChanged = true,
                fieldConfigsChanged = true
            )
        )
        assertEquals(
            FloatingCardSettingsAction.RENDER_CURRENT,
            resolveFloatingCardSettingsAction(
                cardVisible = true,
                hasCurrentWord = true,
                wordSequenceChanged = false,
                fieldConfigsChanged = true
            )
        )
    }
}
