package com.chen.memorizewords.domain.sync.appupdate

import javax.inject.Inject

class CheckAppUpdateUseCase @Inject constructor(
    private val repository: AppUpdateRepository
) {
    suspend operator fun invoke(request: AppUpdateCheck): Result<AppUpdateCheckResult> {
        return repository.checkUpdate(request)
    }
}

class AppUpdatePromptPolicy @Inject constructor() {
    fun shouldPrompt(
        info: AppUpdateInfo,
        dismissRecord: AppUpdateDismissRecord?,
        nowMillis: Long
    ): Boolean {
        if (info.forceUpdate) return true
        if (dismissRecord == null || dismissRecord.releaseId != info.releaseId) return true
        return nowMillis - dismissRecord.dismissedAtMillis >= DEFER_INTERVAL_MILLIS
    }

    fun cachedForceUpdateOrNull(
        cached: AppUpdateCachedForceUpdate?,
        nowMillis: Long
    ): AppUpdateInfo? {
        val info = cached?.info ?: return null
        if (!info.forceUpdate) return null
        val ageMillis = nowMillis - cached.cachedAtMillis
        return if (ageMillis in 0..FORCE_CACHE_TTL_MILLIS) info else null
    }

    companion object {
        const val DEFER_INTERVAL_MILLIS = 24L * 60L * 60L * 1000L
        const val FORCE_CACHE_TTL_MILLIS = 72L * 60L * 60L * 1000L
    }
}
