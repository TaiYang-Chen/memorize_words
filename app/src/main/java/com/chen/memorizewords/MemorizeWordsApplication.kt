package com.chen.memorizewords

import android.app.ActivityManager
import android.app.Application
import android.os.Build
import android.util.Log
import androidx.work.Configuration
import com.chen.memorizewords.core.common.coroutines.ApplicationScope
import com.chen.memorizewords.startup.ApplicationStartupManager
import com.chen.memorizewords.startup.LocalAssetResetter
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope

@HiltAndroidApp
class MemorizeWordsApplication : Application(), Configuration.Provider {

    private val appEntryPoint by lazy(LazyThreadSafetyMode.NONE) {
        EntryPointAccessors.fromApplication(
            this,
            MemorizeWordsApplicationEntryPoint::class.java
        )
    }

    override fun onCreate() {
        super.onCreate()
        if (isFloatingProcess()) return

        val resetReport = LocalAssetResetter.resetLegacyAssetsIfNeeded(this)
        Log.i("LocalAssetResetter", resetReport.summary())

        appEntryPoint.applicationStartupManager()
            .start(application = this, appScope = appEntryPoint.applicationScope())
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

    @ApplicationScope
    fun applicationScope(): CoroutineScope
}
