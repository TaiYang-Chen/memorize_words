package com.chen.memorizewords.domain.sync.appupdate

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
    val fileSizeBytes: Long?
) {
    val versionSpan: String
        get() = "v${currentVersion.versionName} \u2192 v${latestVersion.versionName}"
}

sealed interface AppUpdateCheckResult {
    data object NoUpdate : AppUpdateCheckResult
    data class UpdateAvailable(val info: AppUpdateInfo) : AppUpdateCheckResult
}

data class AppUpdateDismissRecord(
    val releaseId: Long,
    val dismissedAtMillis: Long
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
    fun getCachedForceUpdate(): AppUpdateCachedForceUpdate?
    fun setCachedForceUpdate(info: AppUpdateInfo?, cachedAtMillis: Long)
}
