package com.chen.memorizewords.startup

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.chen.memorizewords.SplashActivity
import com.chen.memorizewords.domain.account.orchestrator.startup.StartupOrchestrator
import com.chen.memorizewords.domain.floating.model.FloatingRuntimePhase
import com.chen.memorizewords.domain.floating.model.FloatingRuntimeSnapshot
import com.chen.memorizewords.domain.floating.repository.CharacterPackRepository
import com.chen.memorizewords.domain.floating.service.FloatingRuntimeController
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch

/**
 * Coordinates foreground recovery after cold launch. It never starts or stops the service
 * directly; the runtime controller restores one persisted session or performs one auto-start.
 */
@Singleton
class PostLaunchStartupTask @Inject constructor(
    private val startupOrchestratorProvider: Provider<StartupOrchestrator>,
    private val networkMonitorProvider: Provider<NetworkMonitor>,
    private val floatingRuntimeControllerProvider: Provider<FloatingRuntimeController>,
    private val characterPackRepository: CharacterPackRepository
) : ApplicationStartupTask {
    override val name: String = TASK_NAME

    override fun start(application: Application, appScope: CoroutineScope, tracer: AppStartupTracer) {
        val firstResumedGate = FirstResumedGate()
        val foregroundTracker = ForegroundTransitionTracker()
        val floatingRuntimeController = floatingRuntimeControllerProvider.get()
        var reconcileJob: Job? = null
        var startDeadlineJob: Job? = null
        var autoStartAttempted = false
        var reconcilePending = false
        var pendingTraceStageName = "post_launch_floating_runtime_change"

        fun scheduleStartingDeadlineCheck(snapshot: FloatingRuntimeSnapshot) {
            val session = snapshot.session
            if (session?.phase != FloatingRuntimePhase.STARTING) {
                startDeadlineJob?.cancel()
                startDeadlineJob = null
                return
            }
            val deadlineAtMs = session.startDeadlineAtMs ?: return
            val sessionId = session.sessionId
            val revision = session.revision
            startDeadlineJob?.cancel()
            startDeadlineJob = appScope.launch {
                delay((deadlineAtMs - System.currentTimeMillis()).coerceAtLeast(0L))
                if (!foregroundTracker.isInForeground) return@launch
                val current = floatingRuntimeController.currentRuntime().session
                if (
                    current?.sessionId != sessionId ||
                    current.revision != revision ||
                    current.phase != FloatingRuntimePhase.STARTING
                ) {
                    return@launch
                }
                try {
                    tracer.measureSuspend(stageName = "post_launch_floating_start_deadline") {
                        floatingRuntimeController.reconcileForeground(allowAutoStart = false)
                    }.also(::scheduleStartingDeadlineCheck)
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

        fun scheduleReconcile(traceStageName: String) {
            if (!foregroundTracker.isInForeground) return
            pendingTraceStageName = traceStageName
            if (reconcileJob?.isActive == true) {
                reconcilePending = true
                return
            }
            reconcileJob = appScope.launch {
                var shouldDelay = true
                do {
                    reconcilePending = false
                    if (shouldDelay) delay(FOREGROUND_RECONCILE_DELAY_MS)
                    shouldDelay = false
                    if (!foregroundTracker.isInForeground) return@launch
                    try {
                        tracer.measureSuspend(stageName = pendingTraceStageName) {
                            floatingRuntimeController.reconcileForeground(
                                allowAutoStart = !autoStartAttempted
                            )
                        }.also(::scheduleStartingDeadlineCheck)
                        autoStartAttempted = true
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        tracer.trace(
                            stageName = "post_launch_floating_reconcile_failed",
                            detail = error::class.java.simpleName
                        )
                    }
                } while (reconcilePending && foregroundTracker.isInForeground)
            }
        }

        // Worker callbacks are best-effort. These durable sources are the recovery boundary for
        // every process change, and this startup task only runs in the main app process.
        appScope.launch {
            merge(
                floatingRuntimeController.observeRuntime().map { Unit },
                characterPackRepository.observeInstalled().map { Unit },
                characterPackRepository.observeDownloads().map { Unit }
            ).collect {
                scheduleReconcile("post_launch_floating_runtime_change")
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
                    startDeadlineJob?.cancel()
                    startDeadlineJob = null
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
