package com.chen.memorizewords.startup

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.provider.Settings
import com.chen.memorizewords.SplashActivity
import com.chen.memorizewords.core.navigation.FloatingWordActions
import com.chen.memorizewords.core.navigation.FloatingWordEntry
import com.chen.memorizewords.data.repository.sync.NetworkMonitor
import com.chen.memorizewords.domain.orchestrator.startup.StartupOrchestrator
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Singleton
class PostLaunchStartupTask @Inject constructor(
    private val startupOrchestratorProvider: Provider<StartupOrchestrator>,
    private val networkMonitorProvider: Provider<NetworkMonitor>,
    private val floatingWordEntryProvider: Provider<FloatingWordEntry>
) : ApplicationStartupTask {
    override val name: String = TASK_NAME

    override fun start(application: Application, appScope: CoroutineScope, tracer: AppStartupTracer) {
        val firstResumedGate = FirstResumedGate()
        application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit

            override fun onActivityStarted(activity: Activity) = Unit

            override fun onActivityResumed(activity: Activity) {
                val isSplashActivity = activity is SplashActivity
                if (!firstResumedGate.onActivityResumed(isSplashActivity = isSplashActivity)) return
                tracer.trace(
                    stageName = "post_launch_first_resume",
                    detail = activity::class.java.name
                )
                application.unregisterActivityLifecycleCallbacks(this)
                appScope.launch {
                    tracer.measureSuspend(stageName = "post_launch_warmup") {
                        startupOrchestratorProvider.get().warmUpSessionStateIfNeeded(
                            hasNetwork = networkMonitorProvider.get().isCurrentlyOnline()
                        )
                    }
                }
                appScope.launch {
                    delay(AUTO_START_DELAY_MS)
                    tracer.measureSuspend(stageName = "post_launch_floating_start") {
                        maybeStartFloatingWord(application)
                    }
                }
            }

            override fun onActivityPaused(activity: Activity) = Unit

            override fun onActivityStopped(activity: Activity) = Unit

            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

            override fun onActivityDestroyed(activity: Activity) = Unit
        })
    }

    private suspend fun maybeStartFloatingWord(application: Application) {
        val shouldAutoStart = startupOrchestratorProvider.get().shouldAutoStartFloating(
            canDrawOverlays = Settings.canDrawOverlays(application)
        )
        if (!shouldAutoStart) return
        floatingWordEntryProvider.get().dispatchServiceAction(
            context = application,
            action = FloatingWordActions.ACTION_START
        )
    }

    companion object {
        private const val AUTO_START_DELAY_MS = 750L
        const val TASK_NAME = "PostLaunchStartupTask"
    }
}
