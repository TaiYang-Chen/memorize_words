package com.chen.memorizewords.domain.sync.appupdate

import com.chen.memorizewords.domain.wordbook.model.download.DownloadRequest
import com.chen.memorizewords.domain.wordbook.model.download.DownloadStatus
import com.chen.memorizewords.domain.wordbook.repository.download.DownloadRepository
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

    fun shouldShowUpdate(
        info: AppUpdateInfo,
        ignoreRecord: AppUpdateIgnoreRecord?,
        deferredRecord: AppUpdateDeferredRecord?,
        nowMillis: Long,
        manual: Boolean
    ): Boolean {
        if (info.forceUpdate || manual) return true
        if (ignoreRecord?.releaseId == info.releaseId ||
            ignoreRecord?.versionCode == info.latestVersion.versionCode
        ) {
            return false
        }
        if (deferredRecord?.releaseId == info.releaseId &&
            deferredRecord.deferredUntilMillis > nowMillis
        ) {
            return false
        }
        return true
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

object VersionComparator {
    fun isRemoteNewer(current: AppVersion, remote: AppVersion): Boolean {
        if (remote.versionCode != current.versionCode) {
            return remote.versionCode > current.versionCode
        }
        return compareVersionName(remote.versionName, current.versionName) > 0
    }

    private fun compareVersionName(left: String, right: String): Int {
        val leftParts = left.split(".", "-", "_").mapNotNull { it.toIntOrNull() }
        val rightParts = right.split(".", "-", "_").mapNotNull { it.toIntOrNull() }
        if (leftParts.isEmpty() || rightParts.isEmpty()) return 0
        val max = maxOf(leftParts.size, rightParts.size)
        for (index in 0 until max) {
            val leftValue = leftParts.getOrElse(index) { 0 }
            val rightValue = rightParts.getOrElse(index) { 0 }
            if (leftValue != rightValue) return leftValue.compareTo(rightValue)
        }
        return 0
    }
}

@Singleton
class AppUpdateManager @Inject constructor(
    private val checkAppUpdate: CheckAppUpdateUseCase,
    private val localStateRepository: AppUpdateLocalStateRepository,
    private val promptPolicy: AppUpdatePromptPolicy,
    private val downloadRepository: DownloadRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _status = MutableStateFlow<AppUpdateStatus>(AppUpdateStatus.Idle)
    val status: StateFlow<AppUpdateStatus> = _status.asStateFlow()
    private var checkJob: Job? = null
    private var downloadJob: Job? = null

    fun check(request: AppUpdateCheck, manual: Boolean = false) {
        checkJob?.cancel()
        checkJob = scope.launch {
            _status.value = AppUpdateStatus.Checking
            val nowMillis = System.currentTimeMillis()
            val result = checkAppUpdate(request).getOrElse { error ->
                val cachedForce = promptPolicy.cachedForceUpdateOrNull(
                    localStateRepository.getCachedForceUpdate(),
                    nowMillis
                )
                if (cachedForce != null) {
                    _status.value = AppUpdateStatus.UpdateAvailable(cachedForce)
                } else {
                    _status.value = AppUpdateStatus.NetworkError(
                        error.message?.takeIf { it.isNotBlank() } ?: "Network unavailable"
                    )
                }
                return@launch
            }
            when (result) {
                AppUpdateCheckResult.NoUpdate -> {
                    localStateRepository.setCachedForceUpdate(null, nowMillis)
                    localStateRepository.setCachedLatestInfo(null)
                    _status.value = AppUpdateStatus.Latest
                }
                is AppUpdateCheckResult.UpdateAvailable -> {
                    val info = result.info.withResolvedPolicy()
                    localStateRepository.setCachedLatestInfo(info)
                    localStateRepository.setCachedForceUpdate(info.takeIf { it.forceUpdate }, nowMillis)
                    if (promptPolicy.shouldShowUpdate(
                            info = info,
                            ignoreRecord = localStateRepository.getIgnoreRecord(),
                            deferredRecord = localStateRepository.getDeferredRecord(),
                            nowMillis = nowMillis,
                            manual = manual
                        )
                    ) {
                        _status.value = AppUpdateStatus.UpdateAvailable(info)
                    } else {
                        _status.value = AppUpdateStatus.Latest
                    }
                }
            }
        }
    }

    fun deferCurrent(nowMillis: Long = System.currentTimeMillis()) {
        val info = currentInfo() ?: return
        if (info.forceUpdate || !info.policy.canDefer) return
        cancelActiveDownload(info)
        val until = nowMillis + info.policy.deferIntervalMillis
        localStateRepository.setDismissed(info.releaseId, nowMillis)
        localStateRepository.setDeferred(info.releaseId, until)
        _status.value = AppUpdateStatus.Deferred(info.releaseId, until)
    }

    fun ignoreCurrent(nowMillis: Long = System.currentTimeMillis()) {
        val info = currentInfo() ?: return
        if (info.forceUpdate || !info.policy.canIgnore) return
        localStateRepository.setIgnored(info.releaseId, info.latestVersion.versionCode, nowMillis)
        _status.value = AppUpdateStatus.Ignored(info.releaseId)
    }

    fun downloadCurrent() {
        val info = currentInfo() ?: return
        download(info)
    }

    fun download(info: AppUpdateInfo) {
        if (downloadJob?.isActive == true && currentInfo()?.releaseId == info.releaseId) return
        downloadJob?.cancel()
        downloadJob = scope.launch {
            val taskId = taskId(info)
            val request = DownloadRequest(
                taskId = taskId,
                url = info.downloadUrl,
                fileName = "memorize_words_${info.latestVersion.versionCode}.apk",
                mimeType = APK_MIME_TYPE,
                displayTitle = "Memorize Words ${info.latestVersion.versionName}",
                displayDesc = "Downloading update package",
                destinationDir = "Download"
            )
            runCatching {
                downloadRepository.start(request)
                downloadRepository.observeState(taskId).first { state ->
                    when (state.status) {
                        DownloadStatus.Downloading -> {
                            _status.value = AppUpdateStatus.Downloading(
                                info = info,
                                progress = state.progress,
                                downloadedBytes = state.downloadedBytes,
                                totalBytes = state.totalBytes
                            )
                            false
                        }
                        DownloadStatus.Completed, DownloadStatus.Failed -> true
                        else -> false
                    }
                }
            }.onSuccess { state ->
                if (state.status == DownloadStatus.Failed) {
                    _status.value = AppUpdateStatus.InstallFailed(info, state.errorMessage ?: "Download failed")
                    return@launch
                }
                val file = File(state.filePath ?: "")
                val verifyError = verify(info, file)
                if (verifyError == null) {
                    _status.value = AppUpdateStatus.Downloaded(info, file)
                } else {
                    _status.value = AppUpdateStatus.VerifyFailed(info, verifyError)
                }
            }.onFailure { error ->
                if (error is CancellationException) return@launch
                _status.value = AppUpdateStatus.InstallFailed(
                    info,
                    error.message?.takeIf { it.isNotBlank() } ?: "Download failed"
                )
            }
        }
    }

    fun markInstalling(info: AppUpdateInfo, file: File) {
        _status.value = AppUpdateStatus.Installing(info, file)
    }

    fun markInstallPermissionRequired(info: AppUpdateInfo, file: File) {
        _status.value = AppUpdateStatus.InstallPermissionRequired(info, file)
    }

    fun markInstallFailed(info: AppUpdateInfo?, message: String) {
        _status.value = AppUpdateStatus.InstallFailed(info, message)
    }

    fun reset() {
        checkJob?.cancel()
        val info = (_status.value as? AppUpdateStatus.Downloading)?.info
        if (info != null) cancelActiveDownload(info)
        _status.value = AppUpdateStatus.Idle
    }

    private fun cancelActiveDownload(info: AppUpdateInfo) {
        downloadJob?.cancel()
        scope.launch(Dispatchers.IO) {
            runCatching { downloadRepository.cancel(taskId(info)) }
        }
    }

    private suspend fun verify(info: AppUpdateInfo, file: File): String? = withContext(Dispatchers.IO) {
        if (!file.exists() || !file.isFile) return@withContext "Update package does not exist"
        val expectedSize = info.fileSizeBytes
        if (expectedSize != null && expectedSize > 0L && file.length() != expectedSize) {
            return@withContext "Update package size verification failed"
        }
        val expectedSha = info.fileSha256?.takeIf { it.isNotBlank() }
        if (expectedSha != null && !sha256(file).equals(expectedSha, ignoreCase = true)) {
            return@withContext "Update package verification failed"
        }
        null
    }

    private fun currentInfo(): AppUpdateInfo? {
        return when (val current = _status.value) {
            is AppUpdateStatus.UpdateAvailable -> current.info
            is AppUpdateStatus.Downloading -> current.info
            is AppUpdateStatus.Downloaded -> current.info
            is AppUpdateStatus.InstallPermissionRequired -> current.info
            is AppUpdateStatus.VerifyFailed -> current.info
            else -> localStateRepository.getCachedLatestInfo()
        }
    }

    private fun AppUpdateInfo.withResolvedPolicy(): AppUpdateInfo {
        return copy(
            policy = policy.copy(
                forceUpdate = forceUpdate,
                canDefer = !forceUpdate && policy.canDefer,
                canIgnore = !forceUpdate && policy.canIgnore
            )
        )
    }

    private fun taskId(info: AppUpdateInfo): String = "app_update_${info.releaseId}"

    private suspend fun sha256(file: File): String = withContext(Dispatchers.IO) {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            var read = input.read(buffer)
            while (read >= 0) {
                digest.update(buffer, 0, read)
                read = input.read(buffer)
            }
        }
        digest.digest().joinToString(separator = "") { byte -> "%02x".format(byte) }
    }

    private companion object {
        const val APK_MIME_TYPE = "application/vnd.android.package-archive"
    }
}
