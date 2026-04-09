package com.chen.memorizewords.startup

class FirstResumedGate {
    private var hasTriggered = false

    // Keep the callback thin and move the one-shot decision into a testable helper.
    fun onActivityResumed(isSplashActivity: Boolean): Boolean {
        if (isSplashActivity || hasTriggered) return false
        hasTriggered = true
        return true
    }
}
