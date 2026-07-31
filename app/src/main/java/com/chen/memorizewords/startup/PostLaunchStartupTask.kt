package com.chen.memorizewords.startup

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.chen.memorizewords.SplashActivity
import com.chen.memorizewords.domain.account.orchestrator.startup.StartupOrchestrator
import com.chen.memorizewords.domain.floating.service.FloatingRuntimeController
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Coordinates foreground recovery after cold launch. It never starts or stops the service
 * directly; the runtime controller restores one persisted session or performs one auto-start.
 */
@Singleton
class PostLaunchStartupTask @Inject constructor(
    private val startupOrchestratorProvider: Provider<StartupOrchestrator>,
    private val networkMonitorProvider: Provider<NetworkMonitor>,
    private val floatingRuntimeControllerProvider: Provider<FloatingRuntimeController>
) : ApplicationStartupTask {
    override val name: String = TASK_NAME

    override fun start(application: Application, appScope: CoroutineScope, tracer: AppStartupTracer) {
        val firstResumedGate = FirstResumedGate()
        val foregroundTracker = ForegroundTransitionTracker()
        var reconcileJob: Job? = null
        var autoStartAttempted = false

        fun scheduleReconcile(traceStageName: String) {
            reconcileJob?.cancel()
            reconcileJob = appScope.launch {
                delay(FOREGROUND_RECONCILE_DELAY_MS)
                if (!foregroundTracker.isInForeground) return@launch
                try {
                    tracer.measureSuspend(stageName = traceStageName) {
                        floatingRuntimeControllerProvider.get().reconcileForeground(
                            allowAutoStart = !autoStartAttempted
                        )
                    }
                    autoStartAttempted = true
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    tracer.trace(
                        stageName = "post_launch_floating_reconcile_failed",
                        detail = error::class.java.simpleName
                    )
                }
            }
        }

        application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit

            override fun onActivityStarted(activity: Activity) {
                foregroundTracker.onActivityStarted()
            }

            override fun onActivityResumed(activity: Activity) {
                val isSplashActivity = activity is SplashActivity
                if (firstResumedGate.onActivityResumed(isSplashActivity)) {
                    tracer.trace("post_launch_first_resume", activity::class.java.name)
                    appScope.launch {
                        tracer.measureSuspend(stageName = "post_launch_warmup") {
                            startupOrchestratorProvider.get().warmUpSessionStateIfNeeded(
                                hasNetwork = networkMonitorProvider.get().isCurrentlyOnline()
                            )
                        }
                    }
                }
                if (isSplashActivity || !foregroundTracker.onActivityResumed()) return
                scheduleReconcile("post_launch_floating_foreground")
            }

            override fun onActivityPaused(activity: Activity) = Unit

            override fun onActivityStopped(activity: Activity) {
                foregroundTracker.onActivityStopped()
                if (!foregroundTracker.isInForeground) {
                    reconcileJob?.cancel()
                    reconcileJob = null
                }
            }

            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

            override fun onActivityDestroyed(activity: Activity) = Unit
        })
    }

    companion object {
        private const val FOREGROUND_RECONCILE_DELAY_MS = 300L
        const val TASK_NAME = "PostLaunchStartupTask"
    }
}
