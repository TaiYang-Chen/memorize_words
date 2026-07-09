package com.chen.memorizewords.data.sync.remoteapi.api.appupdate

data class AppUpdateCheckRequestDto(
    val platform: String,
    val versionCode: Int,
    val versionName: String,
    val channel: String,
    val packageName: String,
    val installId: String
)

data class AppUpdateCheckResponseDto(
    val hasUpdate: Boolean = false,
    val updateAvailable: Boolean = false,
    val forceUpdate: Boolean = false,
    val releaseId: Long? = null,
    val currentVersion: VersionInfoDto? = null,
    val latestVersion: VersionInfoDto? = null,
    val versionName: String? = null,
    val versionCode: Int? = null,
    val releaseNotes: String? = null,
    val releaseNoteItems: List<String>? = null,
    val downloadUrl: String? = null,
    val fileSha256: String? = null,
    val fileSizeBytes: Long? = null,
    val publishedAtMs: Long? = null,
    val riskTips: List<String>? = null,
    val releaseLogUrl: String? = null
)

data class VersionInfoDto(
    val versionName: String? = null,
    val versionCode: Int? = null
)
