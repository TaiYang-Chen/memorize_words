package com.chen.memorizewords.startup

class ForegroundTransitionTracker {
    private val lock = Any()
    private var startedActivityCount = 0
    private var wasInBackground = true

    val isInForeground: Boolean
        get() = synchronized(lock) {
            startedActivityCount > 0 && !wasInBackground
        }

    // Activity callbacks still drive the lifecycle, but the transition state lives here for tests.
    fun onActivityStarted() {
        synchronized(lock) {
            val becameForeground = startedActivityCount++ == 0
            if (becameForeground) {
                wasInBackground = true
            }
        }
    }

    fun onActivityResumed(): Boolean {
        return synchronized(lock) {
            if (!wasInBackground) return@synchronized false
            wasInBackground = false
            true
        }
    }

    fun onActivityStopped() {
        synchronized(lock) {
            startedActivityCount -= 1
            if (startedActivityCount <= 0) {
                startedActivityCount = 0
                wasInBackground = true
            }
        }
    }
}
