package com.chen.memorizewords.startup

import android.app.Application
import kotlinx.coroutines.CoroutineScope

interface ApplicationStartupTask {
    val name: String

    fun start(
        application: Application,
        appScope: CoroutineScope,
        tracer: AppStartupTracer
    )
}
