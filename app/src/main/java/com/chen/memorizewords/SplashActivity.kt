package com.chen.memorizewords

import android.os.Bundle
import android.os.SystemClock
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.chen.memorizewords.core.navigation.AuthEntry
import com.chen.memorizewords.core.navigation.HomeEntry
import com.chen.memorizewords.core.navigation.OnboardingEntry
import com.chen.memorizewords.data.repository.sync.NetworkMonitor
import com.chen.memorizewords.domain.orchestrator.startup.StartupLaunchDestination
import com.chen.memorizewords.domain.orchestrator.startup.StartupOrchestrator
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SplashActivity : AppCompatActivity() {

    companion object {
        private const val MIN_SPLASH_DISPLAY_DURATION_MS = 500L
    }

    @Inject
    lateinit var startupOrchestrator: StartupOrchestrator

    @Inject
    lateinit var homeEntry: HomeEntry

    @Inject
    lateinit var authEntry: AuthEntry

    @Inject
    lateinit var onboardingEntry: OnboardingEntry

    @Inject
    lateinit var networkMonitor: NetworkMonitor

    private var hasRouted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        window.setBackgroundDrawableResource(R.color.app_splash_background)
        setContentView(R.layout.activity_splash)

        val splashShownAt = SystemClock.elapsedRealtime()

        lifecycleScope.launch {
            val targetIntent = when (
                startupOrchestrator.resolveLaunchDestination(
                    hasNetwork = networkMonitor.isCurrentlyOnline()
                )
            ) {
                StartupLaunchDestination.HOME -> homeEntry.createHomeIntent(this@SplashActivity)
                StartupLaunchDestination.ONBOARDING ->
                    onboardingEntry.createOnboardingIntent(this@SplashActivity)
                StartupLaunchDestination.AUTH -> authEntry.createAuthIntent(this@SplashActivity)
            }
            val elapsed = SystemClock.elapsedRealtime() - splashShownAt
            val remaining = MIN_SPLASH_DISPLAY_DURATION_MS - elapsed
            if (remaining > 0) {
                delay(remaining)
            }
            routeToTarget(targetIntent)
        }
    }

    private fun routeToTarget(targetIntent: android.content.Intent) {
        if (hasRouted) return
        hasRouted = true
        startActivity(targetIntent)
        finish()
    }
}
