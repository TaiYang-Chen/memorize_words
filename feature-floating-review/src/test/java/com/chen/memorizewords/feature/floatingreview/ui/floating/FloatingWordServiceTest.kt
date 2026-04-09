package com.chen.memorizewords.feature.floatingreview.ui.floating

import org.junit.Assert.assertEquals
import org.junit.Test

class FloatingWordServiceTest {

    @Test
    fun `resolveCardAlpha converts percent to alpha`() {
        assertEquals(0f, resolveCardAlpha(0), 0.0001f)
        assertEquals(0.5f, resolveCardAlpha(50), 0.0001f)
        assertEquals(1f, resolveCardAlpha(100), 0.0001f)
    }

    @Test
    fun `resolveCardAlpha clamps out of range values`() {
        assertEquals(0f, resolveCardAlpha(-20), 0.0001f)
        assertEquals(1f, resolveCardAlpha(180), 0.0001f)
    }
}
