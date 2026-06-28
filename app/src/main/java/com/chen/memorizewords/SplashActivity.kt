package com.chen.memorizewords

import android.os.Bundle
import android.os.SystemClock
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.chen.memorizewords.core.navigation.AppRoute
import com.chen.memorizewords.domain.account.orchestrator.startup.StartupOrchestrator
import com.chen.memorizewords.startup.NetworkMonitor
import com.chen.memorizewords.startup.StartupRouteIntentFactory
import com.chen.memorizewords.startup.StartupRouteResolver
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SplashActivity : AppCompatActivity() {

    companion object {
        private const val MIN_SPLASH_DISPLAY_DURATION_MS = 1000L
    }

    @Inject
    lateinit var startupOrchestrator: StartupOrchestrator

    @Inject
    lateinit var startupRouteResolver: StartupRouteResolver

    @Inject
    lateinit var startupRouteIntentFactory: StartupRouteIntentFactory

    @Inject
    lateinit var networkMonitor: NetworkMonitor

    private var hasRouted = false
    private var keepSplashOnScreen = true

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        splashScreen.setKeepOnScreenCondition { keepSplashOnScreen }
        super.onCreate(savedInstanceState)
        window.setBackgroundDrawableResource(R.color.app_splash_background)

        val splashShownAt = SystemClock.elapsedRealtime()

        lifecycleScope.launch {
            val targetRoute = startupRouteResolver.resolveRoute(
                startupOrchestrator.resolveLaunchDestination(
                    hasNetwork = networkMonitor.isCurrentlyOnline()
                )
            )
            val elapsed = SystemClock.elapsedRealtime() - splashShownAt
            val remaining = MIN_SPLASH_DISPLAY_DURATION_MS - elapsed
            if (remaining > 0) {
                delay(remaining)
            }
            routeToTarget(targetRoute)
        }
    }

    private fun routeToTarget(targetRoute: AppRoute) {
        if (hasRouted) return
        hasRouted = true
        keepSplashOnScreen = false
        startActivity(startupRouteIntentFactory.createIntent(this, targetRoute))
        finish()
    }
}
