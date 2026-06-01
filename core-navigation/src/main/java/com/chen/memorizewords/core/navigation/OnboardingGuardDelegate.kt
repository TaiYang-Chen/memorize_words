package com.chen.memorizewords.core.navigation

import android.app.Activity

interface OnboardingGuardDelegate {
    fun guard(activity: Activity): Boolean
}
