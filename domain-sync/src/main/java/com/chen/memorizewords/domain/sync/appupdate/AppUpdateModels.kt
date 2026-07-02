package com.chen.memorizewords.domain.sync.appupdate

import java.io.File

data class AppVersion(
    val versionName: String,
    val versionCode: Int
)

data class AppUpdateCheck(
    val platform: String,
    val versionName: String,
    val versionCode: Int,
    val channel: String,
    val packageName: String,
    val installId: String
)

data class AppUpdateInfo(
    val releaseId: Long,
    val currentVersion: AppVersion,
    val latestVersion: AppVersion,
    val forceUpdate: Boolean,
    val releaseNotes: List<String>,
    val downloadUrl: String,
    val fileSha256: String?,
    val fileSizeBytes: Long?,
    val publishedAt: String? = null,
    val riskTips: List<String> = emptyList(),
    val releaseLogUrl: String? = null,
    val policy: AppUpdatePolicy = AppUpdatePolicy()
) {
    val versionSpan: String
        get() = "v${currentVersion.versionName} \u2192 v${latestVersion.versionName}"
}

data class AppUpdatePolicy(
    val forceUpdate: Boolean = false,
    val canDefer: Boolean = true,
    val canIgnore: Boolean = true,
    val deferIntervalMillis: Long = AppUpdatePromptPolicy.DEFER_INTERVAL_MILLIS
)

sealed interface AppUpdateCheckResult {
    data object NoUpdate : AppUpdateCheckResult
    data class UpdateAvailable(val info: AppUpdateInfo) : AppUpdateCheckResult
}

sealed interface AppUpdateStatus {
    data object Idle : AppUpdateStatus
    data object Checking : AppUpdateStatus
    data object Latest : AppUpdateStatus
    data class UpdateAvailable(val info: AppUpdateInfo) : AppUpdateStatus
    data class Downloading(
        val info: AppUpdateInfo,
        val progress: Int,
        val downloadedBytes: Long,
        val totalBytes: Long
    ) : AppUpdateStatus
    data class Downloaded(val info: AppUpdateInfo, val file: File) : AppUpdateStatus
    data class Installing(val info: AppUpdateInfo, val file: File) : AppUpdateStatus
    data class InstallPermissionRequired(val info: AppUpdateInfo, val file: File) : AppUpdateStatus
    data class InstallFailed(val info: AppUpdateInfo?, val message: String) : AppUpdateStatus
    data class NetworkError(val message: String) : AppUpdateStatus
    data class VerifyFailed(val info: AppUpdateInfo, val message: String) : AppUpdateStatus
    data class Ignored(val releaseId: Long) : AppUpdateStatus
    data class Deferred(val releaseId: Long, val untilMillis: Long) : AppUpdateStatus
}

sealed interface AppUpdateAction {
    data object Check : AppUpdateAction
    data object UpdateNow : AppUpdateAction
    data object InstallNow : AppUpdateAction
    data object RemindLater : AppUpdateAction
    data object IgnoreVersion : AppUpdateAction
    data object ViewReleaseLog : AppUpdateAction
    data object Retry : AppUpdateAction
}

data class AppUpdateDismissRecord(
    val releaseId: Long,
    val dismissedAtMillis: Long
)

data class AppUpdateIgnoreRecord(
    val releaseId: Long,
    val versionCode: Int,
    val ignoredAtMillis: Long
)

data class AppUpdateDeferredRecord(
    val releaseId: Long,
    val deferredUntilMillis: Long
)

data class AppUpdateCachedForceUpdate(
    val info: AppUpdateInfo,
    val cachedAtMillis: Long
)

interface AppUpdateRepository {
    suspend fun checkUpdate(request: AppUpdateCheck): Result<AppUpdateCheckResult>
}

interface AppUpdateLocalStateRepository {
    fun getOrCreateInstallId(): String
    fun getDismissRecord(): AppUpdateDismissRecord?
    fun setDismissed(releaseId: Long, dismissedAtMillis: Long)
    fun getIgnoreRecord(): AppUpdateIgnoreRecord?
    fun setIgnored(releaseId: Long, versionCode: Int, ignoredAtMillis: Long)
    fun getDeferredRecord(): AppUpdateDeferredRecord?
    fun setDeferred(releaseId: Long, deferredUntilMillis: Long)
    fun getCachedLatestInfo(): AppUpdateInfo?
    fun setCachedLatestInfo(info: AppUpdateInfo?)
    fun getCachedForceUpdate(): AppUpdateCachedForceUpdate?
    fun setCachedForceUpdate(info: AppUpdateInfo?, cachedAtMillis: Long)
}
