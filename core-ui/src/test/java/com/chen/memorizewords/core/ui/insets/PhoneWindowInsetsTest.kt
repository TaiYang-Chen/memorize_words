package com.chen.memorizewords.core.ui.insets

import androidx.core.graphics.Insets
import android.view.View
import kotlin.test.Test
import kotlin.test.assertEquals

class PhoneWindowInsetsTest {

    @Test
    fun `safe insets retain the largest protected edge from every source`() {
        val result = maxInsets(
            listOf(
                Insets.of(0, 96, 0, 120),
                Insets.of(24, 0, 24, 32),
                Insets.of(0, 0, 0, 680)
            )
        )

        assertEquals(Insets.of(24, 96, 24, 680), result)
    }

    @Test
    fun `relative insets map physical edges to start and end in RTL`() {
        val insets = Insets.of(12, 24, 36, 48)

        assertEquals(
            PhoneRelativeInsets(start = 12, top = 24, end = 36, bottom = 48),
            insets.toPhoneRelativeInsets(
                sides = PhoneInsetSide.entries.toSet(),
                layoutDirection = View.LAYOUT_DIRECTION_LTR
            )
        )
        assertEquals(
            PhoneRelativeInsets(start = 36, top = 24, end = 12, bottom = 48),
            insets.toPhoneRelativeInsets(
                sides = PhoneInsetSide.entries.toSet(),
                layoutDirection = View.LAYOUT_DIRECTION_RTL
            )
        )
    }
}
