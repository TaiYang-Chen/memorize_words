package com.chen.memorizewords.data.repository.wordbook.update

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
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.chen.memorizewords.data.local.room.AppDatabase
import com.chen.memorizewords.data.local.room.model.wordbook.wordbook.WordBookDao
import com.chen.memorizewords.data.local.room.model.wordbook.wordbook.WordBookEntity
import com.chen.memorizewords.data.local.room.model.wordbook.wordbook.toEntity
import com.chen.memorizewords.data.local.room.wordbook.WordBookSyncStateStore
import com.chen.memorizewords.data.remote.datasync.RemoteUserSyncDataSource
import com.chen.memorizewords.data.remote.wordbook.RemoteWordBookDataSource
import com.chen.memorizewords.data.repository.wordbook.persistWordBookPage
import com.chen.memorizewords.domain.model.wordbook.WordBookUpdateApplyMode
import com.chen.memorizewords.domain.model.wordbook.WordBookUpdateExecutionMode
import com.google.gson.Gson
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.io.IOException
import kotlin.math.max
import kotlin.math.min

class CurrentWordBookUpdateWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val bookId = inputData.getLong(CurrentWordBookUpdateWorkConstants.KEY_BOOK_ID, -1L)
        val targetVersion = inputData.getLong(CurrentWordBookUpdateWorkConstants.KEY_TARGET_VERSION, 0L)
        val executionMode = runCatching {
            WordBookUpdateExecutionMode.valueOf(
                inputData.getString(CurrentWordBookUpdateWorkConstants.KEY_EXECUTION_MODE)
                    ?: WordBookUpdateExecutionMode.MANUAL.name
            )
        }.getOrDefault(WordBookUpdateExecutionMode.MANUAL)
        if (bookId <= 0L || targetVersion <= 0L) {
            return failure(bookId, targetVersion, "Invalid update request")
        }

        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext,
            CurrentWordBookUpdateWorkerEntryPoint::class.java
        )
        val remoteUserSyncDataSource = entryPoint.remoteUserSyncDataSource()
        val remoteWordBookDataSource = entryPoint.remoteWordBookDataSource()
        val appDatabase = entryPoint.appDatabase()
        val wordBookDao = entryPoint.wordBookDao()
        val syncStateStore = entryPoint.wordBookSyncStateStore()

        val bookName = wordBookDao.getWordBookById(bookId)?.title.orEmpty()
        val notificationId = buildNotificationId(bookId)
        ensureNotificationChannel()

        return try {
            syncStateStore.clearFailure(bookId)
            setRunningProgress(bookId, targetVersion, 0)
            updateForeground(notificationId, bookName, 0)

            val manifest = remoteUserSyncDataSource
                .getCurrentWordBookUpdateManifest(targetVersion)
                .getOrThrow()
            val applyMode = runCatching {
                WordBookUpdateApplyMode.valueOf(manifest.applyMode)
            }.getOrDefault(WordBookUpdateApplyMode.FULL)

            when (applyMode) {
                WordBookUpdateApplyMode.DELTA -> applyDeltaUpdate(
                    bookId = bookId,
                    targetVersion = targetVersion,
                    removedWordIds = manifest.removedWordIds,
                    upsertWordCount = manifest.upsertWordCount,
                    pageSize = manifest.pageSize,
                    appDatabase = appDatabase,
                    remoteUserSyncDataSource = remoteUserSyncDataSource
                )

                WordBookUpdateApplyMode.FULL -> applyFullRefresh(
                    bookId = bookId,
                    targetVersion = targetVersion,
                    totalHint = wordBookDao.getWordBookById(bookId)?.totalWords ?: 0,
                    pageSize = manifest.pageSize,
                    appDatabase = appDatabase,
                    remoteWordBookDataSource = remoteWordBookDataSource,
                    remoteUserSyncDataSource = remoteUserSyncDataSource
                )
            }

            upsertLatestBookMetadata(
                bookId = bookId,
                targetVersion = targetVersion,
                remoteUserSyncDataSource = remoteUserSyncDataSource,
                wordBookDao = wordBookDao
            )
            syncStateStore.markCompleted(bookId, targetVersion, System.currentTimeMillis())
            remoteUserSyncDataSource.completeCurrentWordBookUpdate(targetVersion).getOrThrow()

            notifyCompleted(notificationId, bookName)
            Result.success(
                workDataOf(
                    CurrentWordBookUpdateWorkConstants.KEY_BOOK_ID to bookId,
                    CurrentWordBookUpdateWorkConstants.KEY_TARGET_VERSION to targetVersion,
                    CurrentWordBookUpdateWorkConstants.KEY_EXECUTION_MODE to executionMode.name,
                    CurrentWordBookUpdateWorkConstants.KEY_PROGRESS to 100
                )
            )
        } catch (_: IOException) {
            Result.retry()
        } catch (t: Throwable) {
            syncStateStore.markFailed(bookId, t.message ?: "Update failed")
            notifyFailed(notificationId, bookName, t.message ?: "Update failed")
            failure(bookId, targetVersion, t.message ?: "Update failed")
        }
    }

    private suspend fun applyDeltaUpdate(
        bookId: Long,
        targetVersion: Long,
        removedWordIds: List<Long>,
        upsertWordCount: Int,
        pageSize: Int,
        appDatabase: AppDatabase,
        remoteUserSyncDataSource: RemoteUserSyncDataSource
    ) {
        val safePageSize = pageSize.coerceAtLeast(DEFAULT_PAGE_SIZE)
        val totalWork = max(removedWordIds.size + upsertWordCount, 1)
        if (removedWordIds.isNotEmpty()) {
            appDatabase.withTransaction {
                appDatabase.wordBookItemDao().deleteWordIds(bookId, removedWordIds)
                appDatabase.wordLearningStateDao().deleteByBookIdAndWordIds(bookId, removedWordIds)
            }
        }
        var processed = removedWordIds.size
        publishProgress(bookId, targetVersion, processed, totalWork)

        var page = 0
        while (true) {
            val pageData = remoteUserSyncDataSource
                .getCurrentWordBookUpdateWords(targetVersion, page, safePageSize)
                .getOrThrow()
            val items = pageData.items
            if (items.isEmpty()) break

            appDatabase.persistWordBookPage(bookId, items)
            processed += items.size
            publishProgress(bookId, targetVersion, processed, totalWork)

            if (upsertWordCount > 0 && processed >= totalWork) {
                break
            }
            page++
        }
    }

    private suspend fun applyFullRefresh(
        bookId: Long,
        targetVersion: Long,
        totalHint: Int,
        pageSize: Int,
        appDatabase: AppDatabase,
        remoteWordBookDataSource: RemoteWordBookDataSource,
        remoteUserSyncDataSource: RemoteUserSyncDataSource
    ) {
        val bookWordItemDao = appDatabase.wordBookItemDao()
        val wordLearningStateDao = appDatabase.wordLearningStateDao()
        appDatabase.withTransaction {
            bookWordItemDao.deleteByBookId(bookId)
            wordLearningStateDao.deleteLearningWordByBookId(bookId)
        }

        var total = totalHint
        val safePageSize = pageSize.coerceAtLeast(DEFAULT_PAGE_SIZE)
        var page = 0
        var downloadedCount = 0
        while (true) {
            val pageData = remoteWordBookDataSource.getBookWords(bookId, page, safePageSize).getOrThrow()
            if (total <= 0 && pageData.total > 0) {
                total = pageData.total.toInt()
            }
            val items = pageData.items
            if (items.isEmpty()) break

            appDatabase.persistWordBookPage(bookId, items)
            downloadedCount = bookWordItemDao.getWordCountByWordBookId(bookId)
            publishProgress(bookId, targetVersion, downloadedCount, max(total, downloadedCount))

            if (total > 0 && downloadedCount >= total) {
                break
            }
            page++
        }

        syncWordStates(
            bookId = bookId,
            pageSize = resolveStatePageSize(total),
            appDatabase = appDatabase,
            remoteUserSyncDataSource = remoteUserSyncDataSource
        )
        publishProgress(bookId, targetVersion, 100, 100)
    }

    private suspend fun syncWordStates(
        bookId: Long,
        pageSize: Int,
        appDatabase: AppDatabase,
        remoteUserSyncDataSource: RemoteUserSyncDataSource
    ) {
        val states = mutableListOf<com.chen.memorizewords.data.local.room.model.study.progress.word.WordLearningStateEntity>()
        var page = 0
        var loaded = 0
        while (true) {
            val pageData = remoteUserSyncDataSource
                .getWordStates(bookId = bookId, page = page, count = pageSize)
                .getOrThrow()
            val items = pageData.items
            if (items.isEmpty()) break
            states += items.map {
                com.chen.memorizewords.data.local.room.model.study.progress.word.WordLearningStateEntity(
                    wordId = it.wordId,
                    bookId = if (it.bookId > 0L) it.bookId else bookId,
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
            loaded += items.size
            if (pageData.total > 0 && loaded.toLong() >= pageData.total) {
                break
            }
            page++
        }

        appDatabase.withTransaction {
            appDatabase.wordLearningStateDao().deleteLearningWordByBookId(bookId)
            if (states.isNotEmpty()) {
                appDatabase.wordLearningStateDao().upsertAll(states)
            }
        }
    }

    private suspend fun upsertLatestBookMetadata(
        bookId: Long,
        targetVersion: Long,
        remoteUserSyncDataSource: RemoteUserSyncDataSource,
        wordBookDao: WordBookDao
    ) {
        val remoteBook = remoteUserSyncDataSource.getMyWordBooks()
            .getOrNull()
            ?.firstOrNull { it.id == bookId }
        when {
            remoteBook != null -> wordBookDao.insertWordBook(remoteBook.toEntity())
            else -> {
                val existing = wordBookDao.getWordBookById(bookId) ?: return
                wordBookDao.insertWordBook(existing.copy(contentVersion = targetVersion))
            }
        }
    }

    private suspend fun publishProgress(
        bookId: Long,
        targetVersion: Long,
        processed: Int,
        total: Int
    ) {
        val progress = toProgress(processed, total)
        setRunningProgress(bookId, targetVersion, progress)
        updateForeground(buildNotificationId(bookId), "", progress)
    }

    private suspend fun setRunningProgress(bookId: Long, targetVersion: Long, progress: Int) {
        setProgress(
            workDataOf(
                CurrentWordBookUpdateWorkConstants.KEY_BOOK_ID to bookId,
                CurrentWordBookUpdateWorkConstants.KEY_TARGET_VERSION to targetVersion,
                CurrentWordBookUpdateWorkConstants.KEY_PROGRESS to progress.coerceIn(0, 100)
            )
        )
    }

    private suspend fun updateForeground(
        notificationId: Int,
        title: String,
        progress: Int
    ) {
        val notification = NotificationCompat.Builder(
            applicationContext,
            CurrentWordBookUpdateWorkConstants.CHANNEL_ID
        )
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(if (title.isBlank()) "Updating word book" else "Updating $title")
            .setContentText("$progress%")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, progress.coerceIn(0, 100), false)
            .build()
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

    private fun notifyCompleted(notificationId: Int, title: String) {
        notifyIfAllowed(
            notificationId,
            NotificationCompat.Builder(applicationContext, CurrentWordBookUpdateWorkConstants.CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle(if (title.isBlank()) "Word book updated" else "Updated: $title")
                .setContentText("Finished")
                .setAutoCancel(true)
                .build()
        )
    }

    private fun notifyFailed(notificationId: Int, title: String, message: String) {
        notifyIfAllowed(
            notificationId,
            NotificationCompat.Builder(applicationContext, CurrentWordBookUpdateWorkConstants.CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_notify_error)
                .setContentTitle(if (title.isBlank()) "Word book update failed" else "Update failed: $title")
                .setContentText(message)
                .setAutoCancel(true)
                .build()
        )
    }

    private fun notifyIfAllowed(notificationId: Int, notification: Notification) {
        if (!canPostNotifications()) return
        runCatching {
            NotificationManagerCompat.from(applicationContext).notify(notificationId, notification)
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
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CurrentWordBookUpdateWorkConstants.CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CurrentWordBookUpdateWorkConstants.CHANNEL_ID,
                "Current word book updates",
                NotificationManager.IMPORTANCE_LOW
            )
        )
    }

    private fun failure(bookId: Long, targetVersion: Long, message: String): Result {
        return Result.failure(
            Data.Builder()
                .putLong(CurrentWordBookUpdateWorkConstants.KEY_BOOK_ID, bookId)
                .putLong(CurrentWordBookUpdateWorkConstants.KEY_TARGET_VERSION, targetVersion)
                .putString(CurrentWordBookUpdateWorkConstants.KEY_ERROR_MESSAGE, message)
                .build()
        )
    }

    private fun buildNotificationId(bookId: Long): Int {
        return (bookId % 1_000_000L).toInt() + 8_000
    }

    private fun toProgress(processed: Int, total: Int): Int {
        if (total <= 0) return 0
        return ((processed * 100) / total).coerceIn(0, 100)
    }

    private fun resolveStatePageSize(totalHint: Int): Int {
        if (totalHint <= 0) return DEFAULT_PAGE_SIZE
        return min(DEFAULT_PAGE_SIZE, totalHint)
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface CurrentWordBookUpdateWorkerEntryPoint {
    fun remoteWordBookDataSource(): RemoteWordBookDataSource
    fun remoteUserSyncDataSource(): RemoteUserSyncDataSource
    fun appDatabase(): AppDatabase
    fun wordBookDao(): WordBookDao
    fun wordBookSyncStateStore(): WordBookSyncStateStore
    fun gson(): Gson
}

private const val DEFAULT_PAGE_SIZE = 50
