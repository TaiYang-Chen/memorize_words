package com.chen.memorizewords.startup

import android.content.Context
import android.content.Intent
import com.chen.memorizewords.core.navigation.AppRoute
import com.chen.memorizewords.core.navigation.AuthEntry
import com.chen.memorizewords.core.navigation.HomeEntry
import com.chen.memorizewords.core.navigation.OnboardingEntry
import javax.inject.Inject

class StartupRouteIntentFactory @Inject constructor(
    private val homeEntry: HomeEntry,
    private val authEntry: AuthEntry,
    private val onboardingEntry: OnboardingEntry
) {

    fun createIntent(context: Context, route: AppRoute): Intent {
        return when (route) {
            AppRoute.Home -> homeEntry.createHomeIntent(context)
            AppRoute.Onboarding -> onboardingEntry.createOnboardingIntent(context)
            is AppRoute.Auth -> authEntry.createAuthIntent(context)
            else -> error("Unsupported startup route: $route")
        }.applyRootTaskFlags()
    }

    internal fun Intent.applyRootTaskFlags(): Intent {
        return apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
    }
}
