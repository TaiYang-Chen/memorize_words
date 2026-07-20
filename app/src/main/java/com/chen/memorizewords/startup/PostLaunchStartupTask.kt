package com.chen.memorizewords.startup

import android.app.Activity
import android.app.Application
import android.app.ForegroundServiceStartNotAllowedException
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import com.chen.memorizewords.SplashActivity
import com.chen.memorizewords.core.navigation.FloatingWordActions
import com.chen.memorizewords.core.navigation.FloatingWordEntry
import com.chen.memorizewords.domain.account.orchestrator.startup.StartupOrchestrator
import com.chen.memorizewords.domain.account.usecase.membership.ObserveMembershipStatusUseCase
import com.chen.memorizewords.domain.floating.model.FloatingActivationContinuation
import com.chen.memorizewords.domain.floating.repository.FloatingWordSettingsRepository
import com.chen.memorizewords.domain.floating.service.FloatingActivationCoordinator
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

internal fun runFloatingServiceStartSafely(
    dispatch: () -> Unit,
    onRejected: (RuntimeException) -> Unit,
    sdkInt: Int = Build.VERSION.SDK_INT
): Boolean {
    return try {
        dispatch()
        true
    } catch (failure: RuntimeException) {
        if (!isExpectedFloatingServiceStartFailure(failure, sdkInt)) throw failure
        onRejected(failure)
        false
    }
}

private fun isExpectedFloatingServiceStartFailure(
    failure: RuntimeException,
    sdkInt: Int
): Boolean =
    failure is IllegalStateException ||
        failure is SecurityException ||
        (
            sdkInt >= Build.VERSION_CODES.S &&
                Android12ForegroundServiceStartFailure.isForegroundServiceStartNotAllowed(failure)
            )

/** Keeps the API 31-only exception class out of the pre-Android 12 code path. */
private object Android12ForegroundServiceStartFailure {
    fun isForegroundServiceStartNotAllowed(failure: RuntimeException): Boolean =
        failure is ForegroundServiceStartNotAllowedException
}

internal fun shouldReconcileForegroundFloatingStart(
    enabled: Boolean,
    autoStartOnAppLaunch: Boolean,
    membershipActive: Boolean,
    appInForeground: Boolean
): Boolean = enabled && autoStartOnAppLaunch && membershipActive && appInForeground

@Singleton
class PostLaunchStartupTask @Inject constructor(
    private val startupOrchestratorProvider: Provider<StartupOrchestrator>,
    private val networkMonitorProvider: Provider<NetworkMonitor>,
    private val floatingWordEntryProvider: Provider<FloatingWordEntry>,
    private val floatingActivationCoordinatorProvider: Provider<FloatingActivationCoordinator>,
    private val floatingWordSettingsRepositoryProvider: Provider<FloatingWordSettingsRepository>,
    private val observeMembershipStatusUseCaseProvider: Provider<ObserveMembershipStatusUseCase>
) : ApplicationStartupTask {
    override val name: String = TASK_NAME

    override fun start(application: Application, appScope: CoroutineScope, tracer: AppStartupTracer) {
        val firstResumedGate = FirstResumedGate()
        val foregroundTracker = ForegroundTransitionTracker()
        var floatingStartJob: Job? = null
        fun scheduleFloatingStart(delayMillis: Long, traceStageName: String) {
            floatingStartJob?.cancel()
            floatingStartJob = appScope.launch {
                delay(delayMillis)
                if (!foregroundTracker.isInForeground) return@launch
                try {
                    tracer.measureSuspend(stageName = traceStageName) {
                        maybeStartFloatingWord(
                            application = application,
                            isAppInForeground = { foregroundTracker.isInForeground },
                            tracer = tracer
                        )
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    tracer.trace(
                        stageName = "post_launch_floating_failed",
                        detail = error::class.java.simpleName
                    )
                }
            }
        }
        appScope.launch {
            combine(
                floatingWordSettingsRepositoryProvider.get().observeSettings(),
                observeMembershipStatusUseCaseProvider.get()()
            ) { settings, membership ->
                shouldReconcileForegroundFloatingStart(
                    enabled = settings.enabled,
                    autoStartOnAppLaunch = settings.autoStartOnAppLaunch,
                    membershipActive = membership?.active == true,
                    appInForeground = foregroundTracker.isInForeground
                )
            }
                .distinctUntilChanged()
                .collect { shouldStart ->
                    if (shouldStart) {
                        scheduleFloatingStart(
                            delayMillis = SETTINGS_RECONCILE_DELAY_MS,
                            traceStageName = "post_launch_floating_settings_reconcile"
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
                if (firstResumedGate.onActivityResumed(isSplashActivity = isSplashActivity)) {
                    tracer.trace(
                        stageName = "post_launch_first_resume",
                        detail = activity::class.java.name
                    )
                    appScope.launch {
                        tracer.measureSuspend(stageName = "post_launch_warmup") {
                            startupOrchestratorProvider.get().warmUpSessionStateIfNeeded(
                                hasNetwork = networkMonitorProvider.get().isCurrentlyOnline()
                            )
                        }
                    }
                }
                if (isSplashActivity || !foregroundTracker.onActivityResumed()) return
                scheduleFloatingStart(
                    delayMillis = AUTO_START_DELAY_MS,
                    traceStageName = "post_launch_floating_foreground"
                )
            }

            override fun onActivityPaused(activity: Activity) = Unit

            override fun onActivityStopped(activity: Activity) {
                foregroundTracker.onActivityStopped()
                if (!foregroundTracker.isInForeground) {
                    floatingStartJob?.cancel()
                    floatingStartJob = null
                }
            }

            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

            override fun onActivityDestroyed(activity: Activity) = Unit
        })
    }

    private suspend fun maybeStartFloatingWord(
        application: Application,
        isAppInForeground: () -> Boolean,
        tracer: AppStartupTracer
    ) {
        if (!isAppInForeground()) return
        val canDrawOverlays = Settings.canDrawOverlays(application)
        val startupOrchestrator = startupOrchestratorProvider.get()
        val activationCoordinator = floatingActivationCoordinatorProvider.get()
        val pendingRequestId = activationCoordinator.getPendingRequestId()
        if (pendingRequestId != null) {
            when (
                activationCoordinator.continueActivation(
                    canDrawOverlays = canDrawOverlays,
                    expectedRequestId = pendingRequestId
                )
            ) {
                FloatingActivationContinuation.ACTIVATED -> {
                    dispatchFloatingStart(
                        application = application,
                        isAppInForeground = isAppInForeground,
                        tracer = tracer,
                        activationRequestId = pendingRequestId
                    )
                    return
                }

                else -> return
            }
        }
        if (!startupOrchestrator.canActivateFloating(canDrawOverlays)) {
            activationCoordinator.disableFloating()
            return
        }
        val shouldAutoStart = startupOrchestrator.shouldAutoStartFloating(canDrawOverlays)
        if (!shouldAutoStart) return
        if (!activationCoordinator.canStartCurrent()) {
            activationCoordinator.disableIfPackMissing()
            return
        }
        dispatchFloatingStart(
            application = application,
            isAppInForeground = isAppInForeground,
            tracer = tracer,
            activationRequestId = null
        )
    }

    private fun dispatchFloatingStart(
        application: Application,
        isAppInForeground: () -> Boolean,
        tracer: AppStartupTracer,
        activationRequestId: String?
    ) {
        if (!isAppInForeground()) return
        runFloatingServiceStartSafely(
            dispatch = {
                floatingWordEntryProvider.get().dispatchServiceAction(
                    context = application,
                    action = FloatingWordActions.ACTION_START,
                    activationRequestId = activationRequestId
                )
            },
            onRejected = { failure ->
                val detail = if (
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    failure is ForegroundServiceStartNotAllowedException
                ) {
                    ForegroundServiceStartNotAllowedException::class.java.simpleName
                } else {
                    failure::class.java.simpleName
                }
                tracer.trace(
                    stageName = "post_launch_floating_skipped",
                    detail = detail
                )
            }
        )
    }

    companion object {
        private const val AUTO_START_DELAY_MS = 750L
        private const val SETTINGS_RECONCILE_DELAY_MS = 250L
        const val TASK_NAME = "PostLaunchStartupTask"
    }
}
