package com.chen.memorizewords.data.repository.download

import android.content.Context
import android.os.Environment
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.chen.memorizewords.data.local.mmkv.download.UpdateDownloadStore
import com.chen.memorizewords.domain.model.download.DownloadRequest
import com.chen.memorizewords.domain.model.download.DownloadState
import com.chen.memorizewords.domain.model.download.DownloadStatus
import com.chen.memorizewords.domain.repository.download.DownloadRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val store: UpdateDownloadStore
) : DownloadRepository {

    private val workManager: WorkManager by lazy {
        WorkManager.getInstance(context)
    }

    override suspend fun start(request: DownloadRequest) {
        if (request.taskId.isBlank() || request.url.isBlank() || request.fileName.isBlank()) return
        val filePath = resolveFilePath(request)
        val existing = store.get(request.taskId)
        val record = UpdateDownloadStore.DownloadRecord(
            taskId = request.taskId,
            url = request.url,
            fileName = request.fileName,
            mimeType = request.mimeType,
            displayTitle = request.displayTitle,
            displayDesc = request.displayDesc,
            destinationDir = request.destinationDir,
            completionAction = request.completionAction,
            filePath = filePath,
            downloadedBytes = existing?.downloadedBytes ?: 0L,
            totalBytes = existing?.totalBytes ?: 0L,
            etag = existing?.etag,
            status = DownloadStatus.Downloading,
            lastError = null
        )
        store.upsert(record)
        enqueueWork(request, filePath)
    }

    override suspend fun pause(taskId: String) {
        if (taskId.isBlank()) return
        workManager.cancelUniqueWork(FileDownloadWorkConstants.uniqueWorkName(taskId))
        store.update(taskId) { record ->
            record.copy(status = DownloadStatus.Paused, lastError = null)
        }
        val record = store.get(taskId) ?: return
        DownloadNotificationHelper.notifyPaused(context, record.toRequest(), record.downloadedBytes, record.totalBytes)
    }

    override suspend fun resume(taskId: String) {
        if (taskId.isBlank()) return
        val record = store.get(taskId) ?: return
        val request = record.toRequest()
        store.update(taskId) { it.copy(status = DownloadStatus.Downloading, lastError = null) }
        enqueueWork(request, record.filePath)
    }

    override suspend fun cancel(taskId: String) {
        if (taskId.isBlank()) return
        workManager.cancelUniqueWork(FileDownloadWorkConstants.uniqueWorkName(taskId))
        val record = store.get(taskId)
        record?.filePath?.let { path ->
            runCatching { File(path).delete() }
        }
        store.remove(taskId)
        DownloadNotificationHelper.cancel(context, taskId)
    }

    override fun observeState(taskId: String): Flow<DownloadState> {
        return store.observeRecords()
            .map { records -> records[taskId] }
            .map { record -> record?.toState() ?: DownloadState(taskId = taskId) }
            .distinctUntilChanged()
    }

    private fun enqueueWork(request: DownloadRequest, filePath: String?) {
        val workRequest = OneTimeWorkRequestBuilder<FileDownloadWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .setInputData(
                workDataOf(
                    FileDownloadWorkConstants.KEY_TASK_ID to request.taskId,
                    FileDownloadWorkConstants.KEY_URL to request.url,
                    FileDownloadWorkConstants.KEY_FILE_NAME to request.fileName,
                    FileDownloadWorkConstants.KEY_MIME_TYPE to request.mimeType,
                    FileDownloadWorkConstants.KEY_DISPLAY_TITLE to request.displayTitle,
                    FileDownloadWorkConstants.KEY_DISPLAY_DESC to request.displayDesc,
                    FileDownloadWorkConstants.KEY_DESTINATION_DIR to request.destinationDir,
                    FileDownloadWorkConstants.KEY_COMPLETION_ACTION to request.completionAction.name,
                    FileDownloadWorkConstants.KEY_FILE_PATH to filePath
                )
            )
            .addTag(FileDownloadWorkConstants.tag(request.taskId))
            .build()

        workManager.enqueueUniqueWork(
            FileDownloadWorkConstants.uniqueWorkName(request.taskId),
            ExistingWorkPolicy.REPLACE,
            workRequest
        )
    }

    private fun resolveFilePath(request: DownloadRequest): String {
        val dirName = request.destinationDir.takeIf { it.isNotBlank() }
            ?: Environment.DIRECTORY_DOWNLOADS
        val targetDir = context.getExternalFilesDir(dirName) ?: context.filesDir
        if (!targetDir.exists()) {
            targetDir.mkdirs()
        }
        return File(targetDir, request.fileName).absolutePath
    }

    private fun UpdateDownloadStore.DownloadRecord.toState(): DownloadState {
        val progress = if (totalBytes > 0L) {
            ((downloadedBytes * 100) / totalBytes).toInt().coerceIn(0, 100)
        } else {
            0
        }
        return DownloadState(
            taskId = taskId,
            status = status,
            progress = progress,
            downloadedBytes = downloadedBytes,
            totalBytes = totalBytes,
            filePath = filePath,
            errorMessage = lastError
        )
    }

    private fun UpdateDownloadStore.DownloadRecord.toRequest(): DownloadRequest {
        return DownloadRequest(
            taskId = taskId,
            url = url,
            fileName = fileName,
            mimeType = mimeType,
            displayTitle = displayTitle,
            displayDesc = displayDesc,
            destinationDir = destinationDir,
            completionAction = completionAction
        )
    }
}

