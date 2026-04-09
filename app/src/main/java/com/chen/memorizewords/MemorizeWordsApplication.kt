package com.chen.memorizewords

import android.app.ActivityManager
import android.app.Application
import android.os.Build
import androidx.work.Configuration
import com.chen.memorizewords.startup.ApplicationStartupManager
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

@HiltAndroidApp
class MemorizeWordsApplication : Application(), Configuration.Provider {

    // Application 只保留全局协程作用域和启动分发，不再直接组装各类启动任务依赖。
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val appEntryPoint by lazy(LazyThreadSafetyMode.NONE) {
        EntryPointAccessors.fromApplication(
            this,
            MemorizeWordsApplicationEntryPoint::class.java
        )
    }

    override fun onCreate() {
        super.onCreate()
        // 浮窗服务运行在独立进程，跳过主进程的启动任务，避免重复初始化。
        if (isFloatingProcess()) return
        appEntryPoint.applicationStartupManager()
            .start(application = this, appScope = appScope)
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().build()

    private fun isFloatingProcess(): Boolean {
        val processName = getProcessNameCompat()
        return processName?.endsWith(":floating") == true
    }

    private fun getProcessNameCompat(): String? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            getProcessName()
        } else {
            val pid = android.os.Process.myPid()
            val manager = getSystemService(ACTIVITY_SERVICE) as? ActivityManager
            manager?.runningAppProcesses?.firstOrNull { it.pid == pid }?.processName
        }
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface MemorizeWordsApplicationEntryPoint {
    fun applicationStartupManager(): ApplicationStartupManager
}
