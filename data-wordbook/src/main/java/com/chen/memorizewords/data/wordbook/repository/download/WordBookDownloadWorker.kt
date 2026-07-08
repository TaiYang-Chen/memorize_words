package com.chen.memorizewords.data.wordbook.repository.download

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.chen.memorizewords.data.wordbook.remote.datasync.RemoteUserSyncDataSource
import com.chen.memorizewords.data.wordbook.repository.WordLearningStateSnapshotStore
import com.chen.memorizewords.data.wordbook.repository.wordbook.WordBookContentDownloader
import com.chen.memorizewords.data.wordbook.repository.wordbook.toProgress
import com.chen.memorizewords.data.wordbook.remoteapi.dto.wordstate.WordStateDto
import com.chen.memorizewords.domain.study.model.progress.word.WordLearningState
import com.chen.memorizewords.domain.study.repository.learning.LearningSyncStatePort
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException
import java.io.IOException

class WordBookDownloadWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val bookId = inputData.getLong(WordBookDownloadWorkConstants.KEY_BOOK_ID, -1L)
        val bookTitle = inputData.getString(WordBookDownloadWorkConstants.KEY_BOOK_TITLE).orEmpty()
        val expectedTotal = inputData.getInt(WordBookDownloadWorkConstants.KEY_TOTAL_WORDS, 0)
        val reportMyBook = inputData.getBoolean(WordBookDownloadWorkConstants.KEY_REPORT_MY_BOOK, false)
        val forceRefresh = inputData.getBoolean(WordBookDownloadWorkConstants.KEY_FORCE_REFRESH, false)
        val targetVersion = inputData.getLong(WordBookDownloadWorkConstants.KEY_TARGET_VERSION, 0L)
        val runInForeground =
            inputData.getBoolean(WordBookDownloadWorkConstants.KEY_RUN_IN_FOREGROUND, true)

        if (bookId <= 0L) {
            return Result.failure(
                Data.Builder()
                    .putLong(WordBookDownloadWorkConstants.KEY_BOOK_ID, bookId)
                    .putString(WordBookDownloadWorkConstants.KEY_ERROR_MESSAGE, "Invalid book id")
                    .putBoolean(WordBookDownloadWorkConstants.KEY_CANCELLED, false)
                    .build()
            )
        }

        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext,
            WordBookDownloadWorkerEntryPoint::class.java
        )
        val contentDownloader = entryPoint.wordBookContentDownloader()
        val remoteUserSyncDataSource = entryPoint.remoteUserSyncDataSource()
        val wordLearningStateSnapshotStore = entryPoint.wordLearningStateSnapshotStore()
        val learningSyncStatePort = entryPoint.learningSyncStatePort()

        val notificationId = buildNotificationId(bookId)
        ensureNotificationChannel()

        var stage = STAGE_START_FOREGROUND
        return try {
            if (runInForeground) {
                updateForeground(notificationId, bookTitle, 0, expectedTotal)
            }
            stage = STAGE_DOWNLOAD_CONTENT
            val downloadResult = contentDownloader.downloadContent(
                bookId = bookId,
                expectedTotal = expectedTotal,
                targetVersion = targetVersion,
                forceRefresh = forceRefresh
            ) { progress ->
                if (isStopped) throw CancellationException()
                setProgress(
                    Data.Builder()
                        .putLong(WordBookDownloadWorkConstants.KEY_BOOK_ID, progress.bookId)
                        .putInt(
                            WordBookDownloadWorkConstants.KEY_DOWNLOADED_WORDS,
                            progress.downloadedWords
                        )
                        .putInt(WordBookDownloadWorkConstants.KEY_TOTAL_WORDS, progress.totalWords)
                        .putInt(WordBookDownloadWorkConstants.KEY_PROGRESS, progress.progressPercent)
                        .build()
                )
                if (runInForeground) {
                    updateForeground(
                        notificationId = notificationId,
                        title = bookTitle,
                        downloadedCount = progress.downloadedWords,
                        total = progress.totalWords
                    )
                }
            }
            if (isStopped) {
                throw CancellationException()
            }

            stage = STAGE_SYNC_WORD_STATES
            syncWordStates(
                bookId = bookId,
                pageSize = DEFAULT_PAGE_SIZE,
                remoteUserSyncDataSource = remoteUserSyncDataSource,
                wordLearningStateSnapshotStore = wordLearningStateSnapshotStore,
                learningSyncStatePort = learningSyncStatePort
            )

            if (reportMyBook) {
                stage = STAGE_ADD_MY_WORD_BOOK
                remoteUserSyncDataSource.addMyWordBook(bookId).getOrThrow()
            }

            stage = STAGE_NOTIFY_COMPLETED
            notifyCompleted(notificationId, bookTitle)
            Result.success(
                Data.Builder()
                    .putLong(WordBookDownloadWorkConstants.KEY_BOOK_ID, bookId)
                    .putInt(
                        WordBookDownloadWorkConstants.KEY_DOWNLOADED_WORDS,
                        downloadResult.downloadedWords
                    )
                    .putInt(WordBookDownloadWorkConstants.KEY_TOTAL_WORDS, downloadResult.totalWords)
                    .putInt(WordBookDownloadWorkConstants.KEY_PROGRESS, downloadResult.progressPercent)
                    .build()
            )
        } catch (_: CancellationException) {
            Result.failure(
                Data.Builder()
                    .putLong(WordBookDownloadWorkConstants.KEY_BOOK_ID, bookId)
                    .putString(
                        WordBookDownloadWorkConstants.KEY_ERROR_MESSAGE,
                        DOWNLOAD_CANCELLED_MESSAGE
                    )
                    .putBoolean(WordBookDownloadWorkConstants.KEY_CANCELLED, true)
                    .build()
            )
        } catch (_: IOException) {
            Result.retry()
        } catch (t: Throwable) {
            val message = "[$stage] ${t.message ?: "Download failed"}"
            Log.e(TAG, "Word book download failed at $stage, bookId=$bookId", t)
            notifyFailed(notificationId, bookTitle, message)
            Result.failure(
                Data.Builder()
                    .putLong(WordBookDownloadWorkConstants.KEY_BOOK_ID, bookId)
                    .putString(WordBookDownloadWorkConstants.KEY_ERROR_MESSAGE, message)
                    .putBoolean(WordBookDownloadWorkConstants.KEY_CANCELLED, false)
                    .build()
            )
        }
    }

    private suspend fun syncWordStates(
        bookId: Long,
        pageSize: Int,
        remoteUserSyncDataSource: RemoteUserSyncDataSource,
        wordLearningStateSnapshotStore: WordLearningStateSnapshotStore,
        learningSyncStatePort: LearningSyncStatePort
    ) {
        if (learningSyncStatePort.hasPendingLearningEvents()) {
            Log.i(TAG, "Skip remote word state snapshot because local learning events are pending")
            return
        }
        val states = mutableListOf<WordLearningState>()
        var page = 0
        var loaded = 0
        while (true) {
            if (isStopped) throw CancellationException()

            val pageData = remoteUserSyncDataSource
                .getWordStates(bookId = bookId, page = page, count = pageSize)
                .getOrThrow()
            val items = pageData.items
            if (items.isEmpty()) break

            states += items.map { it.toDomainState(bookId) }
            loaded += items.size

            if (pageData.total > 0 && loaded.toLong() >= pageData.total) {
                break
            }
            page++
        }

        wordLearningStateSnapshotStore.overwriteLearningStatesForBookFromRemote(bookId, states)
    }

    private fun WordStateDto.toDomainState(fallbackBookId: Long): WordLearningState {
        return WordLearningState(
            wordId = wordId,
            bookId = if (bookId > 0L) bookId else fallbackBookId,
            totalLearnCount = totalLearnCount,
            lastLearnTime = lastLearnTime,
            nextReviewTime = nextReviewTime,
            masteryLevel = masteryLevel,
            userStatus = userStatus,
            repetition = repetition,
            interval = interval,
            efactor = efactor
        )
    }

    private suspend fun updateForeground(
        notificationId: Int,
        title: String,
        downloadedCount: Int,
        total: Int
    ) {
        val progress = toProgress(downloadedCount, total)
        val notification = buildDownloadingNotification(title, progress, downloadedCount, total)
        val foregroundInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                notificationId,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(notificationId, notification)
        }
        setForeground(foregroundInfo)
    }

    private fun buildDownloadingNotification(
        title: String,
        progress: Int,
        downloadedCount: Int,
        total: Int
    ): Notification {
        val content = if (total > 0) {
            "$progress% ($downloadedCount/$total)"
        } else {
            "$progress%"
        }
        return NotificationCompat.Builder(applicationContext, WordBookDownloadWorkConstants.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(if (title.isBlank()) "Downloading word book" else "Downloading $title")
            .setContentText(content)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, progress, false)
            .build()
    }

    private fun notifyCompleted(notificationId: Int, title: String) {
        val notification = NotificationCompat.Builder(
            applicationContext,
            WordBookDownloadWorkConstants.CHANNEL_ID
        )
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(if (title.isBlank()) "Download completed" else "Download completed: $title")
            .setContentText("Finished")
            .setAutoCancel(true)
            .setOngoing(false)
            .build()
        notifyIfAllowed(notificationId, notification)
    }

    private fun notifyFailed(notificationId: Int, title: String, message: String) {
        val notification = NotificationCompat.Builder(
            applicationContext,
            WordBookDownloadWorkConstants.CHANNEL_ID
        )
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle(if (title.isBlank()) "Download failed" else "Download failed: $title")
            .setContentText(message)
            .setAutoCancel(true)
            .setOngoing(false)
            .build()
        notifyIfAllowed(notificationId, notification)
    }

    @SuppressLint("MissingPermission")
    private fun notifyIfAllowed(notificationId: Int, notification: Notification) {
        if (!canPostNotifications()) return
        try {
            NotificationManagerCompat.from(applicationContext).notify(notificationId, notification)
        } catch (_: SecurityException) {
        }
    }

    private fun canPostNotifications(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager =
            applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val existing = manager.getNotificationChannel(WordBookDownloadWorkConstants.CHANNEL_ID)
        if (existing != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                WordBookDownloadWorkConstants.CHANNEL_ID,
                "Word book download",
                NotificationManager.IMPORTANCE_LOW
            )
        )
    }

    private fun buildNotificationId(bookId: Long): Int {
        return (bookId % 1_000_000L).toInt() + 3_000
    }

}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WordBookDownloadWorkerEntryPoint {
    fun wordBookContentDownloader(): WordBookContentDownloader
    fun remoteUserSyncDataSource(): RemoteUserSyncDataSource
    fun wordLearningStateSnapshotStore(): WordLearningStateSnapshotStore
    fun learningSyncStatePort(): LearningSyncStatePort
}

private const val DEFAULT_PAGE_SIZE = 50
private const val DOWNLOAD_CANCELLED_MESSAGE = "Download cancelled"
private const val TAG = "WordBookDownloadWorker"
private const val STAGE_START_FOREGROUND = "start_foreground"
private const val STAGE_DOWNLOAD_CONTENT = "download_content"
private const val STAGE_SYNC_WORD_STATES = "sync_word_states"
private const val STAGE_ADD_MY_WORD_BOOK = "add_my_wordbook"
private const val STAGE_NOTIFY_COMPLETED = "notify_completed"
