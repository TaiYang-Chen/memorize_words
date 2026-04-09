package com.chen.memorizewords.startup

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PromptSafeActivityClassifier @Inject constructor() {

    // The classifier stays pure so the fragile UI-name checks can be verified without Android runtime.
    fun isPromptSafe(
        activityClassName: String,
        visibleFragmentClassName: String?,
        isSplashActivity: Boolean
    ): Boolean {
        if (isSplashActivity) return false
        if (containsBlockedKeyword(activityClassName)) return false
        return !containsBlockedKeyword(visibleFragmentClassName)
    }

    private fun containsBlockedKeyword(className: String?): Boolean {
        if (className.isNullOrBlank()) return false
        return className.contains("learning", ignoreCase = true) ||
            className.contains("practice", ignoreCase = true)
    }
}
