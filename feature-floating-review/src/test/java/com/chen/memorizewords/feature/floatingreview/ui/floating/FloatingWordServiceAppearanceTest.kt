package com.chen.memorizewords.feature.floatingreview.ui.floating

import kotlin.test.Test
import kotlin.test.assertEquals

class FloatingWordServiceAppearanceTest {

    @Test
    fun `ball size scale follows supported percentage range`() {
        assertEquals(0.01f, resolveBallSizeScale(1))
        assertEquals(0.6f, resolveBallSizeScale(60))
        assertEquals(2f, resolveBallSizeScale(200))
    }

    @Test
    fun `ball size scale clamps unsupported percentage`() {
        assertEquals(0.01f, resolveBallSizeScale(0))
        assertEquals(2f, resolveBallSizeScale(201))
    }
}
