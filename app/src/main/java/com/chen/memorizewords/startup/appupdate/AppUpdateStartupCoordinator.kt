package com.chen.memorizewords.startup.appupdate

import com.chen.memorizewords.BuildConfig
import com.chen.memorizewords.domain.sync.appupdate.AppUpdateCheck
import com.chen.memorizewords.domain.sync.appupdate.AppUpdateCheckResult
import com.chen.memorizewords.domain.sync.appupdate.AppUpdateInfo
import com.chen.memorizewords.domain.sync.appupdate.AppUpdateLocalStateRepository
import com.chen.memorizewords.domain.sync.appupdate.AppUpdatePromptPolicy
import com.chen.memorizewords.domain.sync.appupdate.CheckAppUpdateUseCase
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppUpdateStartupCoordinator @Inject constructor(
    private val checkAppUpdate: CheckAppUpdateUseCase,
    private val localStateRepository: AppUpdateLocalStateRepository,
    private val promptPolicy: AppUpdatePromptPolicy
) {
    suspend fun resolveStartupPrompt(nowMillis: Long = System.currentTimeMillis()): AppUpdateInfo? {
        val request = AppUpdateCheck(
            platform = "ANDROID",
            versionName = BuildConfig.VERSION_NAME,
            versionCode = BuildConfig.VERSION_CODE,
            channel = APP_UPDATE_CHANNEL,
            packageName = BuildConfig.APPLICATION_ID,
            installId = localStateRepository.getOrCreateInstallId()
        )
        val result = checkAppUpdate(request).getOrElse {
            return promptPolicy.cachedForceUpdateOrNull(
                cached = localStateRepository.getCachedForceUpdate(),
                nowMillis = nowMillis
            )
        }
        return when (result) {
            AppUpdateCheckResult.NoUpdate -> {
                localStateRepository.setCachedForceUpdate(null, nowMillis)
                null
            }
            is AppUpdateCheckResult.UpdateAvailable -> {
                val info = result.info
                localStateRepository.setCachedForceUpdate(info.takeIf { it.forceUpdate }, nowMillis)
                if (promptPolicy.shouldPrompt(info, localStateRepository.getDismissRecord(), nowMillis)) {
                    info
                } else {
                    null
                }
            }
        }
    }

    fun defer(releaseId: Long, nowMillis: Long = System.currentTimeMillis()) {
        localStateRepository.setDismissed(releaseId, nowMillis)
    }

    private companion object {
        const val APP_UPDATE_CHANNEL = "DEFAULT"
    }
}
