package com.chen.memorizewords.startup

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.chen.memorizewords.domain.account.auth.AuthStateProvider
import com.chen.memorizewords.domain.study.service.DailyStudyProjectionCoordinator
import com.chen.memorizewords.domain.sync.model.PostLoginBootstrapState
import com.chen.memorizewords.domain.sync.service.SyncFacade
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

@Singleton
class DailyStudyProjectionStartupTask @Inject constructor(
    private val authStateProvider: AuthStateProvider,
    private val syncFacade: SyncFacade,
    private val projectionCoordinator: DailyStudyProjectionCoordinator
) : ApplicationStartupTask {
    override val name: String = TASK_NAME

    override fun start(application: Application, appScope: CoroutineScope, tracer: AppStartupTracer) {
        observeForeground(application, appScope, tracer)
        appScope.launch {
            combine(
                authStateProvider.observeAuthenticated(),
                syncFacade.observePostLoginBootstrapState()
            ) { authenticated, bootstrapState ->
                authenticated && bootstrapState == PostLoginBootstrapState.Succeeded
            }
                .distinctUntilChanged()
                .filter { it }
                .collect {
                    recover("post_login_sync", tracer)
                }
        }
    }

    private fun observeForeground(
        application: Application,
        appScope: CoroutineScope,
        tracer: AppStartupTracer
    ) {
        val foregroundTracker = ForegroundTransitionTracker()
        application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit

            override fun onActivityStarted(activity: Activity) {
                foregroundTracker.onActivityStarted()
            }

            override fun onActivityResumed(activity: Activity) {
                if (!foregroundTracker.onActivityResumed()) return
                appScope.launch {
                    if (authStateProvider.isAuthenticated()) recover("foreground", tracer)
                }
            }

            override fun onActivityPaused(activity: Activity) = Unit

            override fun onActivityStopped(activity: Activity) {
                foregroundTracker.onActivityStopped()
            }

            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

            override fun onActivityDestroyed(activity: Activity) = Unit
        })
    }

    private suspend fun recover(trigger: String, tracer: AppStartupTracer) {
        try {
            tracer.measureSuspend("daily_study_projection_recovery", trigger) {
                projectionCoordinator.drainPending()
                projectionCoordinator.reconcileCurrentDay()
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            tracer.trace(
                stageName = "daily_study_projection_recovery_failed",
                detail = "$trigger:${failure::class.java.simpleName}"
            )
        }
    }

    companion object {
        const val TASK_NAME = "DailyStudyProjectionStartupTask"
    }
}
