package com.chen.memorizewords.feature.floatingreview.ui.floating

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FloatingCardCriticalPathPolicyTest {
    @Test
    fun `unchanged card coordinates skip WindowManager update`() {
        assertFalse(shouldUpdateFloatingCardWindow(10, 20, 10, 20))
        assertTrue(shouldUpdateFloatingCardWindow(10, 20, 11, 20))
        assertTrue(shouldUpdateFloatingCardWindow(10, 20, 10, 21))
    }

    @Test
    fun `notification updates are coalesced`() {
        assertEquals(
            FloatingNotificationUpdateAction.KEEP,
            resolveFloatingNotificationUpdateAction("old", "next", "next")
        )
        assertEquals(
            FloatingNotificationUpdateAction.CANCEL_PENDING,
            resolveFloatingNotificationUpdateAction("old", "next", "old")
        )
        assertEquals(
            FloatingNotificationUpdateAction.REPLACE_PENDING,
            resolveFloatingNotificationUpdateAction("old", null, "new")
        )
    }

    @Test
    fun `notification Binder is delayed beyond the first frame`() {
        assertTrue(FLOATING_NOTIFICATION_FIRST_FRAME_DELAY_MS >= 32L)
    }
}
