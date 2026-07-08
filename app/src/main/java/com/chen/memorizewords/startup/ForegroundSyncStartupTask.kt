package com.chen.memorizewords.startup

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.chen.memorizewords.domain.account.auth.AuthStateProvider
import com.chen.memorizewords.domain.sync.service.SyncFacade
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Singleton
class ForegroundSyncStartupTask @Inject constructor(
    private val authStateProvider: AuthStateProvider,
    private val syncFacade: SyncFacade
) : ApplicationStartupTask {
    override val name: String = TASK_NAME

    override fun start(application: Application, appScope: CoroutineScope, tracer: AppStartupTracer) {
        val foregroundTracker = ForegroundTransitionTracker()
        application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit

            override fun onActivityStarted(activity: Activity) {
                foregroundTracker.onActivityStarted()
            }

            override fun onActivityResumed(activity: Activity) {
                if (!foregroundTracker.onActivityResumed()) return
                appScope.launch {
                    tracer.measureSuspend(stageName = "sync_first_foreground") {
                        if (authStateProvider.isAuthenticated()) {
                            syncFacade.scheduleBootstrapSync()
                            syncFacade.triggerDrain()
                        }
                    }
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

    companion object {
        const val TASK_NAME = "ForegroundSyncStartupTask"
    }
}
