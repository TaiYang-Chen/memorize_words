package com.chen.memorizewords.data.repository.download

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.SystemClock
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.chen.memorizewords.data.local.mmkv.download.UpdateDownloadStore
import com.chen.memorizewords.domain.model.download.DownloadCompletionAction
import com.chen.memorizewords.domain.model.download.DownloadRequest
import com.chen.memorizewords.domain.model.download.DownloadStatus
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile

class FileDownloadWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val taskId = inputData.getString(FileDownloadWorkConstants.KEY_TASK_ID).orEmpty()
        val url = inputData.getString(FileDownloadWorkConstants.KEY_URL).orEmpty()
        val fileName = inputData.getString(FileDownloadWorkConstants.KEY_FILE_NAME).orEmpty()
        val mimeType = inputData.getString(FileDownloadWorkConstants.KEY_MIME_TYPE).orEmpty()
            .ifBlank { "application/octet-stream" }
        val displayTitle = inputData.getString(FileDownloadWorkConstants.KEY_DISPLAY_TITLE).orEmpty()
        val displayDesc = inputData.getString(FileDownloadWorkConstants.KEY_DISPLAY_DESC).orEmpty()
        val destinationDir = inputData.getString(FileDownloadWorkConstants.KEY_DESTINATION_DIR).orEmpty()
        val completionActionName = inputData.getString(FileDownloadWorkConstants.KEY_COMPLETION_ACTION)
        val completionAction = runCatching {
            DownloadCompletionAction.valueOf(completionActionName.orEmpty())
        }.getOrDefault(DownloadCompletionAction.None)

        if (taskId.isBlank() || url.isBlank() || fileName.isBlank()) {
            return Result.failure(
                Data.Builder()
                    .putString(FileDownloadWorkConstants.KEY_TASK_ID, taskId)
                    .putString(FileDownloadWorkConstants.KEY_ERROR_MESSAGE, "Invalid download request")
                    .build()
            )
        }

        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext,
            FileDownloadWorkerEntryPoint::class.java
        )
        val store = entryPoint.downloadStore()
        val okHttpClient = entryPoint.okHttpClient()

        val filePath = inputData.getString(FileDownloadWorkConstants.KEY_FILE_PATH)
            ?.takeIf { it.isNotBlank() }
            ?: resolveFilePath(fileName, destinationDir)

        val request = DownloadRequest(
            taskId = taskId,
            url = url,
            fileName = fileName,
            mimeType = mimeType,
            displayTitle = displayTitle,
            displayDesc = displayDesc,
            destinationDir = destinationDir,
            completionAction = completionAction
        )

        return try {
            downloadFile(store, okHttpClient, request, filePath)
            Result.success(
                workDataOf(
                    FileDownloadWorkConstants.KEY_TASK_ID to taskId,
                    FileDownloadWorkConstants.KEY_FILE_PATH to filePath
                )
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (t: Throwable) {
            val message = t.message ?: "Download failed"
            store.update(taskId) { record ->
                record.copy(status = DownloadStatus.Failed, lastError = message)
            }
            DownloadNotificationHelper.notifyFailed(applicationContext, request, message)
            Result.failure(
                Data.Builder()
                    .putString(FileDownloadWorkConstants.KEY_TASK_ID, taskId)
                    .putString(FileDownloadWorkConstants.KEY_ERROR_MESSAGE, message)
                    .build()
            )
        }
    }

    private suspend fun downloadFile(
        store: UpdateDownloadStore,
        okHttpClient: OkHttpClient,
        request: DownloadRequest,
        filePath: String
    ) {
        val existing = store.get(request.taskId)
        val file = File(filePath)
        val parent = file.parentFile
        if (parent != null && !parent.exists()) {
            parent.mkdirs()
        }

        if (existing == null) {
            store.upsert(
                UpdateDownloadStore.DownloadRecord(
                    taskId = request.taskId,
                    url = request.url,
                    fileName = request.fileName,
                    mimeType = request.mimeType,
                    displayTitle = request.displayTitle,
                    displayDesc = request.displayDesc,
                    destinationDir = request.destinationDir,
                    completionAction = request.completionAction,
                    filePath = filePath,
                    downloadedBytes = 0L,
                    totalBytes = 0L,
                    etag = null,
                    status = DownloadStatus.Downloading,
                    lastError = null
                )
            )
        }

        var downloadedBytes = existing?.downloadedBytes ?: 0L
        if (file.exists()) {
            val fileSize = file.length()
            if (fileSize < downloadedBytes) {
                downloadedBytes = fileSize
            }
        } else {
            downloadedBytes = 0L
        }

        var etag = existing?.etag
        var response = executeRequest(okHttpClient, request.url, downloadedBytes, etag)

        if (downloadedBytes > 0L && response.code == 200) {
            response.close()
            file.delete()
            downloadedBytes = 0L
            etag = null
            store.update(request.taskId) { record ->
                record.copy(downloadedBytes = 0L, totalBytes = 0L, etag = null)
            }
            response = executeRequest(okHttpClient, request.url, 0L, null)
        }

        if (downloadedBytes > 0L && response.code == 416) {
            response.close()
            store.update(request.taskId) { record ->
                record.copy(status = DownloadStatus.Completed, lastError = null)
            }
            DownloadNotificationHelper.notifyCompleted(applicationContext, request, file.absolutePath)
            return
        }

        if (!response.isSuccessful) {
            response.close()
            throw IOException("HTTP ${response.code}")
        }

        val body = response.body ?: throw IOException("Empty body")
        val contentLength = body.contentLength()
        val totalBytes = if (contentLength > 0L) downloadedBytes + contentLength else 0L
        etag = response.header("ETag") ?: etag

        store.update(request.taskId) { record ->
            record.copy(
                filePath = filePath,
                downloadedBytes = downloadedBytes,
                totalBytes = totalBytes,
                etag = etag,
                status = DownloadStatus.Downloading,
                lastError = null
            )
        }

        updateForeground(request, downloadedBytes, totalBytes)

        body.byteStream().use { input ->
            RandomAccessFile(file, "rw").use { raf ->
                if (downloadedBytes > 0L) {
                    raf.seek(downloadedBytes)
                }

                val buffer = ByteArray(BUFFER_SIZE)
                var lastUpdate = SystemClock.elapsedRealtime()
                var read = input.read(buffer)
                while (read >= 0) {
                    raf.write(buffer, 0, read)
                    downloadedBytes += read

                    val now = SystemClock.elapsedRealtime()
                    if (now - lastUpdate >= UPDATE_INTERVAL_MS) {
                        lastUpdate = now
                        reportProgress(store, request, downloadedBytes, totalBytes)
                    }

                    if (isStopped) throw CancellationException()
                    read = input.read(buffer)
                }
            }
        }

        reportProgress(store, request, downloadedBytes, totalBytes)
        store.update(request.taskId) { record ->
            record.copy(
                downloadedBytes = downloadedBytes,
                totalBytes = totalBytes,
                status = DownloadStatus.Completed,
                lastError = null
            )
        }

        DownloadNotificationHelper.notifyCompleted(applicationContext, request, file.absolutePath)
    }

    private suspend fun reportProgress(
        store: UpdateDownloadStore,
        request: DownloadRequest,
        downloadedBytes: Long,
        totalBytes: Long
    ) {
        val progress = if (totalBytes > 0L) {
            ((downloadedBytes * 100) / totalBytes).toInt().coerceIn(0, 100)
        } else {
            0
        }
        store.update(request.taskId) { record ->
            record.copy(downloadedBytes = downloadedBytes, totalBytes = totalBytes, status = DownloadStatus.Downloading)
        }
        setProgress(
            Data.Builder()
                .putString(FileDownloadWorkConstants.KEY_TASK_ID, request.taskId)
                .putLong(FileDownloadWorkConstants.KEY_DOWNLOADED_BYTES, downloadedBytes)
                .putLong(FileDownloadWorkConstants.KEY_TOTAL_BYTES, totalBytes)
                .putInt(FileDownloadWorkConstants.KEY_PROGRESS, progress)
                .build()
        )
        updateForeground(request, downloadedBytes, totalBytes)
        DownloadNotificationHelper.notifyDownloading(
            applicationContext,
            request,
            DownloadStatus.Downloading,
            downloadedBytes,
            totalBytes
        )
    }

    private suspend fun updateForeground(
        request: DownloadRequest,
        downloadedBytes: Long,
        totalBytes: Long
    ) {
        val notification = DownloadNotificationHelper.buildDownloadingNotification(
            applicationContext,
            request,
            DownloadStatus.Downloading,
            downloadedBytes,
            totalBytes
        )
        val foregroundInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                DownloadNotificationHelper.notificationId(request.taskId),
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(DownloadNotificationHelper.notificationId(request.taskId), notification)
        }
        setForeground(foregroundInfo)
    }

    private fun executeRequest(
        okHttpClient: OkHttpClient,
        url: String,
        downloadedBytes: Long,
        etag: String?
    ): okhttp3.Response {
        val builder = Request.Builder().url(url)
        if (downloadedBytes > 0L) {
            builder.addHeader("Range", "bytes=$downloadedBytes-")
            if (!etag.isNullOrBlank()) {
                builder.addHeader("If-Range", etag)
            }
        }
        return okHttpClient.newCall(builder.build()).execute()
    }

    private fun resolveFilePath(fileName: String, destinationDir: String): String {
        val dirName = destinationDir.takeIf { it.isNotBlank() }
            ?: Environment.DIRECTORY_DOWNLOADS
        val targetDir = applicationContext.getExternalFilesDir(dirName) ?: applicationContext.filesDir
        if (!targetDir.exists()) {
            targetDir.mkdirs()
        }
        return File(targetDir, fileName).absolutePath
    }

}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface FileDownloadWorkerEntryPoint {
    fun okHttpClient(): OkHttpClient
    fun downloadStore(): UpdateDownloadStore
}

private const val BUFFER_SIZE = 8 * 1024
private const val UPDATE_INTERVAL_MS = 600L
