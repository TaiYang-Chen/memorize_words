package com.chen.memorizewords.feature.floatingreview.ui.floating

import android.view.WindowManager
import kotlin.test.Test
import kotlin.test.assertTrue

class FloatingWindowLayoutFlagsTest {

    @Test
    fun `floating ball window keeps interaction flags and enables hardware acceleration`() {
        val flags = floatingBallWindowFlags()

        assertTrue(flags hasFlag WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
        assertTrue(flags hasFlag WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN)
        assertTrue(flags hasFlag WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
        assertTrue(flags hasFlag WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED)
    }

    private infix fun Int.hasFlag(flag: Int): Boolean = this and flag == flag
}
