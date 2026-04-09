package com.chen.memorizewords.startup

class ForegroundTransitionTracker {
    private var startedActivityCount = 0
    private var wasInBackground = true

    // Activity callbacks still drive the lifecycle, but the transition state lives here for tests.
    fun onActivityStarted() {
        val becameForeground = startedActivityCount++ == 0
        if (becameForeground) {
            wasInBackground = true
        }
    }

    fun onActivityResumed(): Boolean {
        if (!wasInBackground) return false
        wasInBackground = false
        return true
    }

    fun onActivityStopped() {
        startedActivityCount -= 1
        if (startedActivityCount <= 0) {
            startedActivityCount = 0
            wasInBackground = true
        }
    }
}
