package com.chen.memorizewords.data.repository.download

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.content.FileProvider
import com.chen.memorizewords.domain.model.download.DownloadRequest
import com.chen.memorizewords.domain.model.download.DownloadStatus
import java.io.File
import java.util.Locale

object DownloadNotificationHelper {

    fun notificationId(taskId: String): Int {
        return (taskId.hashCode() and 0x7fffffff) % 1_000_000 + 2_000
    }

    fun notifyDownloading(
        context: Context,
        request: DownloadRequest,
        status: DownloadStatus,
        downloadedBytes: Long,
        totalBytes: Long
    ) {
        if (!canPostNotifications(context)) return
        ensureChannel(context)
        val notificationId = notificationId(request.taskId)
        notify(
            context,
            notificationId,
            buildDownloadingNotification(context, request, status, downloadedBytes, totalBytes)
        )
    }

    fun notifyPaused(
        context: Context,
        request: DownloadRequest,
        downloadedBytes: Long,
        totalBytes: Long
    ) {
        if (!canPostNotifications(context)) return
        ensureChannel(context)
        val notificationId = notificationId(request.taskId)
        notify(
            context,
            notificationId,
            buildPausedNotification(context, request, downloadedBytes, totalBytes)
        )
    }

    fun notifyCompleted(context: Context, request: DownloadRequest, filePath: String?) {
        if (!canPostNotifications(context)) return
        ensureChannel(context)
        val notificationId = notificationId(request.taskId)
        val completionPendingIntent = completionPendingIntent(context, request, filePath)
        val notification = NotificationCompat.Builder(context, FileDownloadWorkConstants.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(resolveTitle(request))
            .setContentText(resolveCompletionContent(request))
            .setAutoCancel(true)
            .setContentIntent(completionPendingIntent)
            .apply {
                buildCompletionAction(context, request, filePath)?.let { action ->
                    addAction(action)
                }
            }
            .build()
        notify(context, notificationId, notification)
    }

    fun notifyFailed(context: Context, request: DownloadRequest, message: String) {
        if (!canPostNotifications(context)) return
        ensureChannel(context)
        val notificationId = notificationId(request.taskId)
        val notification = NotificationCompat.Builder(context, FileDownloadWorkConstants.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle(resolveTitle(request))
            .setContentText(message)
            .setAutoCancel(true)
            .addAction(
                android.R.drawable.ic_media_play,
                "继续",
                actionPendingIntent(context, FileDownloadWorkConstants.ACTION_RESUME, request.taskId)
            )
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "取消",
                actionPendingIntent(context, FileDownloadWorkConstants.ACTION_CANCEL, request.taskId)
            )
            .build()
        notify(context, notificationId, notification)
    }

    fun cancel(context: Context, taskId: String) {
        try {
            NotificationManagerCompat.from(context).cancel(notificationId(taskId))
        } catch (_: SecurityException) {
        }
    }

    fun buildDownloadingNotification(
        context: Context,
        request: DownloadRequest,
        status: DownloadStatus,
        downloadedBytes: Long,
        totalBytes: Long
    ): Notification {
        val progress = computeProgress(downloadedBytes, totalBytes)
        val builder = NotificationCompat.Builder(context, FileDownloadWorkConstants.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(resolveTitle(request))
            .setContentText(resolveContent(request, progress, downloadedBytes, totalBytes))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, progress, totalBytes <= 0L)

        if (status == DownloadStatus.Downloading) {
            builder.addAction(
                android.R.drawable.ic_media_pause,
                "暂停",
                actionPendingIntent(context, FileDownloadWorkConstants.ACTION_PAUSE, request.taskId)
            )
        } else if (status == DownloadStatus.Paused) {
            builder.addAction(
                android.R.drawable.ic_media_play,
                "继续",
                actionPendingIntent(context, FileDownloadWorkConstants.ACTION_RESUME, request.taskId)
            )
        }
        builder.addAction(
            android.R.drawable.ic_menu_close_clear_cancel,
            "取消",
            actionPendingIntent(context, FileDownloadWorkConstants.ACTION_CANCEL, request.taskId)
        )
        return builder.build()
    }

    fun buildPausedNotification(
        context: Context,
        request: DownloadRequest,
        downloadedBytes: Long,
        totalBytes: Long
    ): Notification {
        val progress = computeProgress(downloadedBytes, totalBytes)
        return NotificationCompat.Builder(context, FileDownloadWorkConstants.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(resolveTitle(request))
            .setContentText(resolveContent(request, progress, downloadedBytes, totalBytes))
            .setOnlyAlertOnce(true)
            .setProgress(100, progress, totalBytes <= 0L)
            .addAction(
                android.R.drawable.ic_media_play,
                "继续",
                actionPendingIntent(context, FileDownloadWorkConstants.ACTION_RESUME, request.taskId)
            )
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "取消",
                actionPendingIntent(context, FileDownloadWorkConstants.ACTION_CANCEL, request.taskId)
            )
            .build()
    }

    private fun notify(context: Context, id: Int, notification: Notification) {
        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (_: SecurityException) {
        }
    }

    private fun resolveTitle(request: DownloadRequest): String {
        return request.displayTitle.takeIf { it.isNotBlank() } ?: "正在下载"
    }

    private fun resolveContent(
        request: DownloadRequest,
        progress: Int,
        downloadedBytes: Long,
        totalBytes: Long
    ): String {
        if (request.displayDesc.isNotBlank()) return request.displayDesc
        return if (totalBytes > 0L) {
            "$progress% (${formatBytes(downloadedBytes)}/${formatBytes(totalBytes)})"
        } else {
            "$progress%"
        }
    }

    private fun computeProgress(downloadedBytes: Long, totalBytes: Long): Int {
        if (totalBytes <= 0L) return 0
        return ((downloadedBytes * 100) / totalBytes).toInt().coerceIn(0, 100)
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes <= 0L) return "0B"
        val kb = bytes / 1024.0
        if (kb < 1024) return String.format(Locale.ROOT, "%.1fKB", kb)
        val mb = kb / 1024.0
        if (mb < 1024) return String.format(Locale.ROOT, "%.1fMB", mb)
        val gb = mb / 1024.0
        return String.format(Locale.ROOT, "%.1fGB", gb)
    }

    private fun resolveCompletionContent(request: DownloadRequest): String {
        return when (request.completionAction) {
            com.chen.memorizewords.domain.model.download.DownloadCompletionAction.OpenFile -> "下载完成，点按打开"
            com.chen.memorizewords.domain.model.download.DownloadCompletionAction.InstallApk -> "下载完成，点按处理安装"
            com.chen.memorizewords.domain.model.download.DownloadCompletionAction.None -> "下载完成"
        }
    }

    private fun actionPendingIntent(context: Context, action: String, taskId: String): PendingIntent {
        val intent = Intent(context, DownloadActionReceiver::class.java).apply {
            this.action = action
            putExtra(FileDownloadWorkConstants.EXTRA_TASK_ID, taskId)
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val requestCode = "$action:$taskId".hashCode()
        return PendingIntent.getBroadcast(context, requestCode, intent, flags)
    }

    private fun completionPendingIntent(
        context: Context,
        request: DownloadRequest,
        filePath: String?
    ): PendingIntent? {
        val intent = completionIntent(context, request, filePath) ?: return null
        return PendingIntent.getActivity(
            context,
            "complete:${request.taskId}".hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun buildCompletionAction(
        context: Context,
        request: DownloadRequest,
        filePath: String?
    ): NotificationCompat.Action? {
        val pendingIntent = completionPendingIntent(context, request, filePath) ?: return null
        val title = when (request.completionAction) {
            com.chen.memorizewords.domain.model.download.DownloadCompletionAction.OpenFile -> "打开"
            com.chen.memorizewords.domain.model.download.DownloadCompletionAction.InstallApk -> "安装"
            com.chen.memorizewords.domain.model.download.DownloadCompletionAction.None -> return null
        }
        return NotificationCompat.Action.Builder(0, title, pendingIntent).build()
    }

    private fun completionIntent(
        context: Context,
        request: DownloadRequest,
        filePath: String?
    ): Intent? {
        val path = filePath?.takeIf { it.isNotBlank() } ?: return null
        val file = File(path)
        if (!file.exists()) return null
        return when (request.completionAction) {
            com.chen.memorizewords.domain.model.download.DownloadCompletionAction.None -> null
            com.chen.memorizewords.domain.model.download.DownloadCompletionAction.OpenFile -> {
                buildOpenFileIntent(context, file, request.mimeType)
            }
            com.chen.memorizewords.domain.model.download.DownloadCompletionAction.InstallApk -> {
                buildInstallIntent(context, file)
            }
        }
    }

    private fun buildOpenFileIntent(context: Context, file: File, mimeType: String): Intent {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun buildInstallIntent(context: Context, file: File): Intent {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            return Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                "package:${context.packageName}".toUri()
            )
        }

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, APK_MIME_TYPE)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val existing = manager.getNotificationChannel(FileDownloadWorkConstants.CHANNEL_ID)
        if (existing != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                FileDownloadWorkConstants.CHANNEL_ID,
                FileDownloadWorkConstants.CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            )
        )
    }

    private fun canPostNotifications(context: Context): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
    }
}

private const val APK_MIME_TYPE = "application/vnd.android.package-archive"
