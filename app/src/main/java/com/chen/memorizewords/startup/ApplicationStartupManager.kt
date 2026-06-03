package com.chen.memorizewords.startup

import android.app.Application
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope

@Singleton
class ApplicationStartupManager @Inject constructor(
    private val tasks: Set<@JvmSuppressWildcards ApplicationStartupTask>,
    private val tracer: AppStartupTracer
) {
    fun start(application: Application, appScope: CoroutineScope) {
        tracer.measure(stageName = "startup_manager_start") {
            orderedTasks().forEach { task ->
                tracer.measure(stageName = "task_registered", detail = task.name) {
                    task.start(application = application, appScope = appScope, tracer = tracer)
                }
            }
        }
    }

    private fun orderedTasks(): List<ApplicationStartupTask> {
        return tasks.sortedBy { task -> TASK_ORDER[task.name] ?: Int.MAX_VALUE }
    }

    private companion object {
        val TASK_ORDER = mapOf(
            SessionKickoutStartupTask.TASK_NAME to 0,
            PostLaunchStartupTask.TASK_NAME to 1,
            ForegroundWordBookStartupTask.TASK_NAME to 2
        )
    }
}
