package com.chen.memorizewords.data.sync.repository.sync

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FailedEventPendingSignal @Inject constructor() {
    private val version = AtomicLong(0L)
    private val pending = AtomicBoolean(true)

    fun hasPending(): Boolean = pending.get()

    fun markPending() {
        version.incrementAndGet()
        pending.set(true)
    }

    fun initialize(hasPending: Boolean) {
        if (hasPending) {
            markPending()
        } else if (version.get() == 0L) {
            pending.set(false)
        }
    }

    fun snapshotVersion(): Long = version.get()

    fun clearIfUnchanged(snapshotVersion: Long) {
        if (version.get() == snapshotVersion) {
            pending.set(false)
        }
    }

    fun clear() {
        version.incrementAndGet()
        pending.set(false)
    }
}
