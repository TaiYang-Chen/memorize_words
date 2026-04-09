package com.chen.memorizewords.startup

import android.app.Application
import android.content.Intent
import com.chen.memorizewords.core.navigation.AuthEntry
import com.chen.memorizewords.domain.orchestrator.startup.StartupOrchestrator
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@Singleton
class SessionKickoutStartupTask @Inject constructor(
    private val startupOrchestratorProvider: Provider<StartupOrchestrator>,
    private val authEntryProvider: Provider<AuthEntry>
) : ApplicationStartupTask {
    override val name: String = TASK_NAME

    override fun start(application: Application, appScope: CoroutineScope, tracer: AppStartupTracer) {
        appScope.launch {
            val startupOrchestrator = tracer.measure(stageName = "session_kickout_observer_start") {
                startupOrchestratorProvider.get()
            }
            startupOrchestrator.sessionKickoutNotifier.events.collectLatest {
                tracer.trace(stageName = "session_kickout_received")
                val loginIntent = authEntryProvider.get().createAuthIntent(application).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                }
                application.startActivity(loginIntent)
            }
        }
    }

    companion object {
        const val TASK_NAME = "SessionKickoutStartupTask"
    }
}
