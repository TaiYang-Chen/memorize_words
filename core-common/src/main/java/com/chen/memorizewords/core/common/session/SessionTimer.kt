package com.chen.memorizewords.core.common.session

class SessionTimer(
    private val nowProvider: () -> Long = { System.nanoTime() / 1_000_000L }
) {

    private var activeStartElapsedMs: Long? = null
    private var totalDurationMs: Long = 0L

    fun reset() {
        activeStartElapsedMs = null
        totalDurationMs = 0L
    }

    fun start() {
        if (activeStartElapsedMs != null) return
        activeStartElapsedMs = nowProvider()
    }

    fun pause(): Long {
        val start = activeStartElapsedMs ?: return 0L
        activeStartElapsedMs = null
        val durationMs = (nowProvider() - start).coerceAtLeast(0L)
        totalDurationMs += durationMs
        return durationMs
    }

    fun finish(): Long {
        pause()
        return totalDurationMs
    }

    fun elapsedTotal(): Long = totalDurationMs
}
