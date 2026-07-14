package com.chen.memorizewords.data.sync.repository.sync

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FailedEventPendingSignalTest {
    @Test
    fun `empty startup initialization suppresses recovery`() {
        val signal = FailedEventPendingSignal()

        signal.initialize(hasPending = false)

        assertFalse(signal.hasPending())
    }

    @Test
    fun `concurrent record prevents stale drain from clearing signal`() {
        val signal = FailedEventPendingSignal()
        signal.initialize(hasPending = false)
        signal.markPending()
        val drainSnapshot = signal.snapshotVersion()

        signal.markPending()
        signal.clearIfUnchanged(drainSnapshot)

        assertTrue(signal.hasPending())
    }

    @Test
    fun `drain clears unchanged pending signal`() {
        val signal = FailedEventPendingSignal()
        signal.markPending()
        val drainSnapshot = signal.snapshotVersion()

        signal.clearIfUnchanged(drainSnapshot)

        assertFalse(signal.hasPending())
    }
}
