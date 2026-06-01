package com.chen.memorizewords.core.common.time

interface AppClock {
    fun nowEpochMillis(): Long
    fun nowElapsedMillis(): Long
}

class SystemAppClock : AppClock {
    override fun nowEpochMillis(): Long = System.currentTimeMillis()

    override fun nowElapsedMillis(): Long = System.nanoTime() / NANOS_PER_MILLI

    private companion object {
        const val NANOS_PER_MILLI = 1_000_000L
    }
}
