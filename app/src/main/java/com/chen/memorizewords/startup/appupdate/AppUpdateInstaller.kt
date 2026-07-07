package com.chen.memorizewords.startup.appupdate

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.FileProvider
import com.chen.memorizewords.BuildConfig
import com.chen.memorizewords.domain.sync.appupdate.AppUpdateInfo
import com.chen.memorizewords.domain.wordbook.model.download.DownloadCompletionAction
import com.chen.memorizewords.domain.wordbook.model.download.DownloadRequest
import com.chen.memorizewords.domain.wordbook.model.download.DownloadStatus
import com.chen.memorizewords.domain.wordbook.repository.download.DownloadRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

@Singleton
class AppUpdateInstaller @Inject constructor(
    @ApplicationContext private val context: Context,
    private val downloadRepository: DownloadRepository
) {
    suspend fun downloadAndVerify(
        info: AppUpdateInfo,
        onProgress: (Int) -> Unit = {}
    ): File {
        val fileName = "memorize_words_${info.latestVersion.versionCode}.apk"
        val taskId = "app_update_${info.releaseId}"
        downloadRepository.start(
            DownloadRequest(
                taskId = taskId,
                url = info.downloadUrl,
                fileName = fileName,
                mimeType = APK_MIME_TYPE,
                displayTitle = "Memorize Words ${info.latestVersion.versionName}",
                displayDesc = "Downloading update package",
                destinationDir = Environment.DIRECTORY_DOWNLOADS,
                completionAction = DownloadCompletionAction.None
            )
        )
        val state = downloadRepository.observeState(taskId)
            .first { state ->
                if (state.status == DownloadStatus.Downloading) {
                    onProgress(state.progress)
                }
                state.status == DownloadStatus.Completed || state.status == DownloadStatus.Failed
            }
        if (state.status == DownloadStatus.Failed) {
            error(state.errorMessage ?: "Update package download failed")
        }
        onProgress(100)
        val path = state.filePath ?: error("Update package path is empty")
        val file = File(path)
        if (!file.exists() || !file.isFile) error("Update package does not exist")
        val expectedSize = info.fileSizeBytes
        if (expectedSize != null && expectedSize > 0L && file.length() != expectedSize) {
            error("Update package size verification failed")
        }
        val expectedSha = info.fileSha256
        if (!expectedSha.isNullOrBlank()) {
            val actualSha = sha256(file)
            check(actualSha.equals(expectedSha, ignoreCase = true)) { "Update package verification failed" }
        }
        return file
    }

    fun canInstallPackages(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
            context.packageManager.canRequestPackageInstalls()
    }

    fun createInstallPermissionIntent(): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${BuildConfig.APPLICATION_ID}")
            )
        } else {
            Intent(Settings.ACTION_SECURITY_SETTINGS)
        }
    }

    fun install(activity: Activity, file: File) {
        val uri = FileProvider.getUriForFile(
            activity,
            "${BuildConfig.APPLICATION_ID}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, APK_MIME_TYPE)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        activity.startActivity(intent)
    }

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
