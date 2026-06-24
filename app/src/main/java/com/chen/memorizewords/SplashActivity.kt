package com.chen.memorizewords

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.splashscreen.SplashScreenViewProvider
import androidx.lifecycle.lifecycleScope
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import com.chen.memorizewords.core.navigation.AuthEntry
import com.chen.memorizewords.core.navigation.HomeEntry
import com.chen.memorizewords.core.navigation.OnboardingEntry
import com.chen.memorizewords.domain.account.orchestrator.startup.StartupLaunchDestination
import com.chen.memorizewords.domain.account.orchestrator.startup.StartupOrchestrator
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SplashActivity : AppCompatActivity() {

    companion object {
        private const val MIN_SPLASH_DISPLAY_DURATION_MS = 1000L
        private const val SPLASH_EXIT_ANIMATION_DURATION_MS = 320L
    }

    @Inject
    lateinit var startupOrchestrator: StartupOrchestrator

    @Inject
    lateinit var homeEntry: HomeEntry

    @Inject
    lateinit var authEntry: AuthEntry

    @Inject
    lateinit var onboardingEntry: OnboardingEntry

    private var hasRouted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        window.setBackgroundDrawableResource(R.color.app_splash_background)
        setContentView(R.layout.activity_splash)
        findViewById<View>(R.id.logo_card).alpha = 0f
        splashScreen.setOnExitAnimationListener(::animateSplashIconToLogo)

        val splashShownAt = SystemClock.elapsedRealtime()

        lifecycleScope.launch {
            val targetIntent = when (startupOrchestrator.resolveLaunchDestinationFast()) {
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

    private fun animateSplashIconToLogo(provider: SplashScreenViewProvider) {
        val targetView = findViewById<View>(R.id.logo_card)
        var hasRemovedProvider = false

        fun finishSplashExit() {
            if (hasRemovedProvider) return
            hasRemovedProvider = true
            targetView.alpha = 1f
            provider.remove()
        }

        val iconView = runCatching { provider.iconView }.getOrNull()
        if (iconView == null) {
            finishSplashExit()
            return
        }

        if (iconView.width == 0 || iconView.height == 0 ||
            targetView.width == 0 || targetView.height == 0
        ) {
            finishSplashExit()
            return
        }

        val iconLocation = IntArray(2)
        val targetLocation = IntArray(2)
        iconView.getLocationInWindow(iconLocation)
        targetView.getLocationInWindow(targetLocation)

        val iconCenterX = iconLocation[0] + iconView.width / 2f
        val iconCenterY = iconLocation[1] + iconView.height / 2f
        val targetCenterX = targetLocation[0] + targetView.width / 2f
        val targetCenterY = targetLocation[1] + targetView.height / 2f

        iconView.pivotX = iconView.width / 2f
        iconView.pivotY = iconView.height / 2f

        iconView.animate()
            .translationX(targetCenterX - iconCenterX)
            .translationY(targetCenterY - iconCenterY)
            .scaleX(targetView.width.toFloat() / iconView.width)
            .scaleY(targetView.height.toFloat() / iconView.height)
            .setDuration(SPLASH_EXIT_ANIMATION_DURATION_MS)
            .setInterpolator(FastOutSlowInInterpolator())
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    finishSplashExit()
                }

                override fun onAnimationCancel(animation: Animator) {
                    finishSplashExit()
                }
            })
            .start()
    }

    private fun routeToTarget(targetIntent: Intent) {
        if (hasRouted) return
        hasRouted = true
        startActivity(targetIntent)
        finish()
    }
}
