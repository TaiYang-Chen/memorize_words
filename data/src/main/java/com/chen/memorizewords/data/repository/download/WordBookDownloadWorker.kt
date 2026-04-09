package com.chen.memorizewords.data.repository.download

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.room.withTransaction
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.chen.memorizewords.data.local.room.AppDatabase
import com.chen.memorizewords.data.local.room.wordbook.WordBookSyncStateStore
import com.chen.memorizewords.data.local.room.model.sync.SyncOutboxDao
import com.chen.memorizewords.data.local.room.model.study.progress.word.WordLearningStateEntity
import com.chen.memorizewords.data.remote.datasync.RemoteUserSyncDataSource
import com.chen.memorizewords.data.remote.wordbook.RemoteWordBookDataSource
import com.chen.memorizewords.data.repository.sync.AddMyWordBookSyncWorker
import com.chen.memorizewords.data.repository.sync.SyncWorkConstants
import com.chen.memorizewords.data.repository.sync.SyncOutboxBizType
import com.chen.memorizewords.data.repository.sync.WordStateDeleteByBookSyncPayload
import com.chen.memorizewords.data.repository.sync.WordStateUpsertSyncPayload
import com.chen.memorizewords.data.repository.wordbook.persistWordBookPage
import com.chen.memorizewords.network.dto.wordstate.WordStateDto
import com.google.gson.Gson
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.math.min

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

        if (bookId <= 0L) {
            return Result.failure(
                Data.Builder()
                    .putLong(WordBookDownloadWorkConstants.KEY_BOOK_ID, bookId)
                    .putString(WordBookDownloadWorkConstants.KEY_ERROR_MESSAGE, "Invalid book id")
                    .build()
            )
        }

        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext,
            WordBookDownloadWorkerEntryPoint::class.java
        )
        val remoteWordBookDataSource = entryPoint.remoteWordBookDataSource()
        val remoteUserSyncDataSource = entryPoint.remoteUserSyncDataSource()
        val db = entryPoint.appDatabase()
        val syncStateStore = entryPoint.wordBookSyncStateStore()
        val bookWordItemDao = db.wordBookItemDao()
        val wordLearningStateDao = db.wordLearningStateDao()
        val syncOutboxDao = entryPoint.syncOutboxDao()
        val gson = entryPoint.gson()

        val notificationId = buildNotificationId(bookId)
        ensureNotificationChannel()

        return try {
            if (forceRefresh) {
                db.withTransaction {
                    bookWordItemDao.deleteByBookId(bookId)
                    wordLearningStateDao.deleteLearningWordByBookId(bookId)
                }
            }

            var downloadedCount = bookWordItemDao.getWordCountByWordBookId(bookId)
            var total = expectedTotal
            var pageSize = resolvePageSize(total)

            updateForeground(notificationId, bookTitle, downloadedCount, total)

            if (total !in 1..downloadedCount) {
                var page = downloadedCount / pageSize
                while (true) {
                    if (isStopped) throw CancellationException()

                    val pageData = remoteWordBookDataSource
                        .getBookWords(bookId, page, pageSize)
                        .getOrThrow()

                    if (total <= 0) {
                        total = if (pageData.total > 0) pageData.total.toInt() else expectedTotal
                        pageSize = resolvePageSize(total)
                    }

                    val items = pageData.items
                    if (items.isEmpty()) break

                    db.persistWordBookPage(bookId, items)

                    downloadedCount = bookWordItemDao.getWordCountByWordBookId(bookId)
                    val progress = toProgress(downloadedCount, total)
                    setProgress(
                        Data.Builder()
                            .putLong(WordBookDownloadWorkConstants.KEY_BOOK_ID, bookId)
                            .putInt(WordBookDownloadWorkConstants.KEY_DOWNLOADED_WORDS, downloadedCount)
                            .putInt(WordBookDownloadWorkConstants.KEY_TOTAL_WORDS, total)
                            .putInt(WordBookDownloadWorkConstants.KEY_PROGRESS, progress)
                            .build()
                    )
                    updateForeground(notificationId, bookTitle, downloadedCount, total)

                    if (total > 0 && downloadedCount >= total) break
                    page++
                }
            }

            syncWordStates(
                bookId = bookId,
                pageSize = pageSize,
                remoteUserSyncDataSource = remoteUserSyncDataSource,
                appDatabase = db,
                wordLearningStateDao = wordLearningStateDao,
                syncOutboxDao = syncOutboxDao,
                gson = gson
            )

            if (reportMyBook) {
                enqueueAddMyWordBookWork(bookId)
            }

            val finalProgress = toProgress(downloadedCount, total)
            notifyCompleted(notificationId, bookTitle)
            if (targetVersion > 0L) {
                syncStateStore.setLocalVersion(bookId, targetVersion)
            }
            Result.success(
                Data.Builder()
                    .putLong(WordBookDownloadWorkConstants.KEY_BOOK_ID, bookId)
                    .putInt(WordBookDownloadWorkConstants.KEY_DOWNLOADED_WORDS, downloadedCount)
                    .putInt(WordBookDownloadWorkConstants.KEY_TOTAL_WORDS, total)
                    .putInt(WordBookDownloadWorkConstants.KEY_PROGRESS, finalProgress)
                    .build()
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: IOException) {
            Result.retry()
        } catch (t: Throwable) {
            val message = t.message ?: "Download failed"
            notifyFailed(notificationId, bookTitle, message)
            Result.failure(
                Data.Builder()
                    .putLong(WordBookDownloadWorkConstants.KEY_BOOK_ID, bookId)
                    .putString(WordBookDownloadWorkConstants.KEY_ERROR_MESSAGE, message)
                    .build()
            )
        }
    }

    private suspend fun syncWordStates(
        bookId: Long,
        pageSize: Int,
        remoteUserSyncDataSource: RemoteUserSyncDataSource,
        appDatabase: AppDatabase,
        wordLearningStateDao: com.chen.memorizewords.data.local.room.model.study.progress.word.WordLearningStateDao,
        syncOutboxDao: SyncOutboxDao,
        gson: Gson
    ) {
        val states = mutableListOf<WordLearningStateEntity>()
        var page = 0
        var loaded = 0
        while (true) {
            if (isStopped) throw CancellationException()

            val pageData = remoteUserSyncDataSource
                .getWordStates(bookId = bookId, page = page, count = pageSize)
                .getOrThrow()
            val items = pageData.items
            if (items.isEmpty()) break

            states += items.map { it.toEntity(bookId) }
            loaded += items.size

            if (pageData.total > 0 && loaded.toLong() >= pageData.total) {
                break
            }
            page++
        }

        appDatabase.withTransaction {
            wordLearningStateDao.deleteLearningWordByBookId(bookId)
            if (states.isNotEmpty()) {
                wordLearningStateDao.upsertAll(states)
            }
            applyPendingLocalWordStateOverrides(
                bookId = bookId,
                syncOutboxDao = syncOutboxDao,
                wordLearningStateDao = wordLearningStateDao,
                gson = gson
            )
        }
    }

    private suspend fun applyPendingLocalWordStateOverrides(
        bookId: Long,
        syncOutboxDao: SyncOutboxDao,
        wordLearningStateDao: com.chen.memorizewords.data.local.room.model.study.progress.word.WordLearningStateDao,
        gson: Gson
    ) {
        val pendingDeletes = syncOutboxDao.getByBizType(SyncOutboxBizType.WORD_STATE_DELETE_BY_BOOK)
            .mapNotNull { entity ->
                runCatching {
                    gson.fromJson(
                        entity.payload,
                        WordStateDeleteByBookSyncPayload::class.java
                    )
                }.getOrNull()
            }
            .filter { it.bookId == bookId }
        if (pendingDeletes.isNotEmpty()) {
            wordLearningStateDao.deleteLearningWordByBookId(bookId)
        }

        val pendingUpserts = syncOutboxDao.getByBizType(SyncOutboxBizType.WORD_STATE_UPSERT)
            .mapNotNull { entity ->
                runCatching {
                    gson.fromJson(entity.payload, WordStateUpsertSyncPayload::class.java)
                }.getOrNull()
            }
            .filter { it.bookId == bookId }
            .map {
                WordLearningStateEntity(
                    wordId = it.wordId,
                    bookId = it.bookId,
                    totalLearnCount = it.totalLearnCount,
                    lastLearnTime = it.lastLearnTime,
                    nextReviewTime = it.nextReviewTime,
                    masteryLevel = it.masteryLevel,
                    userStatus = it.userStatus,
                    repetition = it.repetition,
                    interval = it.interval,
                    efactor = it.efactor
                )
            }
        if (pendingUpserts.isNotEmpty()) {
            wordLearningStateDao.upsertAll(pendingUpserts)
        }
    }

    private fun WordStateDto.toEntity(fallbackBookId: Long): WordLearningStateEntity {
        return WordLearningStateEntity(
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

    private fun enqueueAddMyWordBookWork(bookId: Long) {
        val request = OneTimeWorkRequestBuilder<AddMyWordBookSyncWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .setInputData(workDataOf(SyncWorkConstants.KEY_BOOK_ID to bookId))
            .addTag(SyncWorkConstants.TAG_ADD_MY_WORD_BOOK)
            .build()

        WorkManager.getInstance(applicationContext).enqueueUniqueWork(
            SyncWorkConstants.addMyWordBookWorkName(bookId),
            ExistingWorkPolicy.KEEP,
            request
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

    private fun toProgress(downloadedCount: Int, total: Int): Int {
        if (total <= 0) return 0
        return ((downloadedCount * 100) / total).coerceIn(0, 100)
    }

    private fun resolvePageSize(totalHint: Int): Int {
        if (totalHint > 0) {
            return min(DEFAULT_PAGE_SIZE, totalHint)
        }
        return DEFAULT_PAGE_SIZE
    }

    private fun buildNotificationId(bookId: Long): Int {
        return (bookId % 1_000_000L).toInt() + 3_000
    }

}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WordBookDownloadWorkerEntryPoint {
    fun remoteWordBookDataSource(): RemoteWordBookDataSource
    fun remoteUserSyncDataSource(): RemoteUserSyncDataSource
    fun appDatabase(): AppDatabase
    fun wordBookSyncStateStore(): WordBookSyncStateStore
    fun syncOutboxDao(): SyncOutboxDao
    fun gson(): Gson
}

private const val DEFAULT_PAGE_SIZE = 50
