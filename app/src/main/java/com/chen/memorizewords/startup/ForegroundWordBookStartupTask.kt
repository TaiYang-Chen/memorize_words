package com.chen.memorizewords.startup

import android.app.Activity
import android.app.Application
import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import com.chen.memorizewords.SplashActivity
import com.chen.memorizewords.domain.service.wordbook.WordBookUpdateCoordinator
import com.chen.memorizewords.feature.home.notification.WordBookUpdateNotifier
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@Singleton
class ForegroundWordBookStartupTask @Inject constructor(
    private val updateCoordinatorProvider: Provider<WordBookUpdateCoordinator>,
    private val wordBookUpdateNotifierProvider: Provider<WordBookUpdateNotifier>,
    private val promptSafeActivityClassifier: PromptSafeActivityClassifier
) : ApplicationStartupTask {
    override val name: String = TASK_NAME

    private var didStartPromptObserver = false

    override fun start(application: Application, appScope: CoroutineScope, tracer: AppStartupTracer) {
        val foregroundTracker = ForegroundTransitionTracker()
        application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit

            override fun onActivityStarted(activity: Activity) {
                foregroundTracker.onActivityStarted()
            }

            override fun onActivityResumed(activity: Activity) {
                if (!foregroundTracker.onActivityResumed()) return
                ensurePromptObserverStarted(appScope = appScope, tracer = tracer)
                val deliverAsNotification = !promptSafeActivityClassifier.isPromptSafe(
                    activityClassName = activity::class.java.name,
                    visibleFragmentClassName = activity.visibleFragmentClassName(),
                    isSplashActivity = activity is SplashActivity
                )
                appScope.launch {
                    tracer.measureSuspend(
                        stageName = "wordbook_first_foreground",
                        detail = "deliverAsNotification=$deliverAsNotification"
                    ) {
                        updateCoordinatorProvider.get()
                            .onAppForeground(deliverAsNotification = deliverAsNotification)
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

    private fun ensurePromptObserverStarted(appScope: CoroutineScope, tracer: AppStartupTracer) {
        if (didStartPromptObserver) return
        didStartPromptObserver = true
        appScope.launch {
            val coordinator = tracer.measure(stageName = "wordbook_prompt_observer_start") {
                updateCoordinatorProvider.get()
            }
            coordinator.observeLocalNotificationPrompts().collectLatest { prompt ->
                wordBookUpdateNotifierProvider.get().notifyUpdate(prompt)
            }
        }
    }

    private fun Activity.visibleFragmentClassName(): String? {
        return (this as? FragmentActivity)
            ?.supportFragmentManager
            ?.fragments
            ?.firstVisibleFragmentClassName()
    }

    private fun List<Fragment>.firstVisibleFragmentClassName(): String? {
        return asReversed().asSequence()
            .filter { it.isVisible }
            .mapNotNull { fragment ->
                fragment.childFragmentManager.fragments.firstVisibleFragmentClassName()
                    ?: fragment::class.java.name
            }
            .firstOrNull()
    }

    companion object {
        const val TASK_NAME = "ForegroundWordBookStartupTask"
    }
}
