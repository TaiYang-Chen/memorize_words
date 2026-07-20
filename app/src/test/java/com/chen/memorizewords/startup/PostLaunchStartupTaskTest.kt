package com.chen.memorizewords.startup

import java.util.concurrent.Executors
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class PostLaunchStartupTaskTest {

    @Test
    fun `settings reconciliation starts only for an active foreground eligible state`() {
        assertTrue(
            shouldReconcileForegroundFloatingStart(
                enabled = true,
                autoStartOnAppLaunch = true,
                membershipActive = true,
                appInForeground = true
            )
        )
        assertFalse(
            shouldReconcileForegroundFloatingStart(
                enabled = true,
                autoStartOnAppLaunch = false,
                membershipActive = true,
                appInForeground = true
            )
        )
        assertFalse(
            shouldReconcileForegroundFloatingStart(
                enabled = true,
                autoStartOnAppLaunch = true,
                membershipActive = false,
                appInForeground = true
            )
        )
        assertFalse(
            shouldReconcileForegroundFloatingStart(
                enabled = true,
                autoStartOnAppLaunch = true,
                membershipActive = true,
                appInForeground = false
            )
        )
    }

    @Test
    fun `delayed floating start gate closes when app returns to background`() {
        val tracker = ForegroundTransitionTracker()

        tracker.onActivityStarted()
        assertFalse(tracker.isInForeground)
        assertTrue(tracker.onActivityResumed())
        assertTrue(tracker.isInForeground)

        tracker.onActivityStopped()

        assertFalse(tracker.isInForeground)
    }

    @Test
    fun `activity handoff keeps delayed floating start gate open`() {
        val tracker = ForegroundTransitionTracker()
        tracker.onActivityStarted()
        tracker.onActivityResumed()

        tracker.onActivityStarted()
        tracker.onActivityStopped()

        assertTrue(tracker.isInForeground)
        assertFalse(tracker.onActivityResumed())
    }

    @Test
    fun `background startup worker observes latest foreground state`() {
        val tracker = ForegroundTransitionTracker()
        val worker = Executors.newSingleThreadExecutor()
        try {
            tracker.onActivityStarted()
            tracker.onActivityResumed()
            assertTrue(worker.submit<Boolean> { tracker.isInForeground }.get())

            tracker.onActivityStopped()
            assertFalse(worker.submit<Boolean> { tracker.isInForeground }.get())
        } finally {
            worker.shutdownNow()
        }
    }

    @Test
    fun `foreground service start rejection is contained`() {
        var rejected: RuntimeException? = null

        val started = runFloatingServiceStartSafely(
            dispatch = { throw IllegalStateException("background start rejected") },
            onRejected = { rejected = it }
        )

        assertFalse(started)
        assertIs<IllegalStateException>(rejected)
    }

    @Test
    fun `foreground service security rejection is contained`() {
        var rejected: RuntimeException? = null

        val started = runFloatingServiceStartSafely(
            dispatch = { throw SecurityException("permission revoked") },
            onRejected = { rejected = it }
        )

        assertFalse(started)
        assertIs<SecurityException>(rejected)
    }

    @Test
    fun `foreground service start success is reported`() {
        var dispatched = false

        val started = runFloatingServiceStartSafely(
            dispatch = { dispatched = true },
            onRejected = { error("unexpected rejection") }
        )

        assertTrue(started)
        assertTrue(dispatched)
    }
}
