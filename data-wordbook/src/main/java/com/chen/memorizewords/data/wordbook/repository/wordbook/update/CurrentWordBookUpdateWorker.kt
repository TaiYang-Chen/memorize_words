package com.chen.memorizewords.data.wordbook.repository.wordbook.update

import android.Manifest
import android.annotation.SuppressLint
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
import com.chen.memorizewords.data.wordbook.local.WordBookDatabase
import com.chen.memorizewords.data.wordbook.local.room.model.wordbook.contentstate.WordBookContentStateDao
import com.chen.memorizewords.data.wordbook.local.room.model.wordbook.contentstate.WordBookContentStateEntity
import com.chen.memorizewords.data.wordbook.local.room.model.wordbook.contentstate.WordBookContentStatus
import com.chen.memorizewords.data.wordbook.local.room.model.wordbook.wordbook.WordBookDao
import com.chen.memorizewords.data.wordbook.local.room.model.wordbook.wordbook.toEntity
import com.chen.memorizewords.data.wordbook.local.room.model.wordbook.wordbook.toDomain
import com.chen.memorizewords.data.wordbook.local.room.wordbook.WordBookSyncStateStore
import com.chen.memorizewords.data.wordbook.remote.datasync.RemoteUserSyncDataSource
import com.chen.memorizewords.data.wordbook.repository.WordLearningStateSnapshotStore
import com.chen.memorizewords.data.wordbook.repository.wordbook.WordBookContentPackageImporter
import com.chen.memorizewords.data.wordbook.repository.wordbook.WordBookPackageImportResult
import com.chen.memorizewords.data.wordbook.repository.wordbook.WordBookPackageValidationException
import com.chen.memorizewords.domain.study.model.progress.word.WordLearningState
import com.chen.memorizewords.domain.study.repository.learning.LearningSyncStatePort
import com.chen.memorizewords.domain.wordbook.model.WordBookUpdateExecutionMode
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.io.IOException
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.CancellationException

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
        val appDatabase = entryPoint.appDatabase()
        val wordBookDao = entryPoint.wordBookDao()
        val syncStateStore = entryPoint.wordBookSyncStateStore()
        val wordLearningStateSnapshotStore = entryPoint.wordLearningStateSnapshotStore()
        val learningSyncStatePort = entryPoint.learningSyncStatePort()
        val packageImporter = entryPoint.wordBookContentPackageImporter()
        val contentStateDao = entryPoint.wordBookContentStateDao()

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
            applyFullRefresh(
                bookId = bookId,
                targetVersion = targetVersion,
                pageSize = manifest.pageSize,
                appDatabase = appDatabase,
                remoteUserSyncDataSource = remoteUserSyncDataSource,
                wordBookDao = wordBookDao,
                packageImporter = packageImporter,
                contentStateDao = contentStateDao,
                wordLearningStateSnapshotStore = wordLearningStateSnapshotStore,
                learningSyncStatePort = learningSyncStatePort
            )

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
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (t: Throwable) {
            syncStateStore.markFailed(bookId, t.message ?: "Update failed")
            notifyFailed(notificationId, bookName, t.message ?: "Update failed")
            failure(bookId, targetVersion, t.message ?: "Update failed")
        }
    }

    private suspend fun applyFullRefresh(
        bookId: Long,
        targetVersion: Long,
        pageSize: Int,
        appDatabase: WordBookDatabase,
        remoteUserSyncDataSource: RemoteUserSyncDataSource,
        wordBookDao: WordBookDao,
        packageImporter: WordBookContentPackageImporter,
        contentStateDao: WordBookContentStateDao,
        wordLearningStateSnapshotStore: WordLearningStateSnapshotStore,
        learningSyncStatePort: LearningSyncStatePort
    ) {
        val bookWordItemDao = appDatabase.wordBookItemDao()
        val safePageSize = pageSize.coerceAtLeast(DEFAULT_PAGE_SIZE)
        var failureTotalWords = 0
        var failurePackageSha256: String? = null

        val importContext = try {
            val latestBook = remoteUserSyncDataSource.getMyWordBooks()
                .getOrThrow()
                .firstOrNull { it.id == bookId }
                ?.toEntity()
                ?: wordBookDao.getWordBookById(bookId)
                ?: throw WordBookPackageValidationException("word book metadata missing")
            if (latestBook.contentVersion != targetVersion) {
                throw WordBookPackageValidationException(
                    "word book version mismatch: expected=$targetVersion, actual=${latestBook.contentVersion}"
                )
            }
            wordBookDao.insertWordBook(latestBook)
            val book = latestBook.toDomain()
            val contentPackage = book.contentPackage
                ?: throw WordBookPackageValidationException("content package missing")
            failureTotalWords = book.totalWords
            failurePackageSha256 = contentPackage.sha256

            markContentState(
                contentStateDao = contentStateDao,
                bookId = bookId,
                targetVersion = targetVersion,
                localVersion = 0L,
                status = WordBookContentStatus.DOWNLOADING,
                downloadedWords = 0,
                totalWords = book.totalWords,
                packageSha256 = contentPackage.sha256,
                lastError = null
            )

            val importResult = packageImporter.importPackage(
                book = book,
                contentPackage = contentPackage,
                beforeImport = {
                    appDatabase.withTransaction {
                        bookWordItemDao.deleteByBookId(bookId)
                    }
                }
            ) { downloaded, total ->
                markContentState(
                    contentStateDao = contentStateDao,
                    bookId = bookId,
                    targetVersion = targetVersion,
                    localVersion = 0L,
                    status = WordBookContentStatus.DOWNLOADING,
                    downloadedWords = downloaded,
                    totalWords = total,
                    packageSha256 = contentPackage.sha256,
                    lastError = null
                )
                publishProgress(bookId, targetVersion, downloaded, max(total, downloaded))
            }
            FullRefreshImportContext(contentPackage.sha256, importResult)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            markContentState(
                contentStateDao = contentStateDao,
                bookId = bookId,
                targetVersion = targetVersion,
                localVersion = 0L,
                status = WordBookContentStatus.FAILED,
                downloadedWords = bookWordItemDao.getWordCountByWordBookId(bookId),
                totalWords = failureTotalWords,
                packageSha256 = failurePackageSha256,
                lastError = throwable.message
            )
            throw throwable
        }

        markContentState(
            contentStateDao = contentStateDao,
            bookId = bookId,
            targetVersion = targetVersion,
            localVersion = targetVersion,
            status = WordBookContentStatus.READY,
            downloadedWords = importContext.importResult.importedWords,
            totalWords = importContext.importResult.totalWords,
            packageSha256 = importContext.packageSha256,
            lastError = null
        )
        syncWordStates(
            bookId = bookId,
            pageSize = resolveStatePageSize(importContext.importResult.totalWords.coerceAtLeast(safePageSize)),
            remoteUserSyncDataSource = remoteUserSyncDataSource,
            wordLearningStateSnapshotStore = wordLearningStateSnapshotStore,
            learningSyncStatePort = learningSyncStatePort
        )
        publishProgress(bookId, targetVersion, 100, 100)
    }

    private suspend fun markContentState(
        contentStateDao: WordBookContentStateDao,
        bookId: Long,
        targetVersion: Long,
        localVersion: Long,
        status: String,
        downloadedWords: Int,
        totalWords: Int,
        packageSha256: String?,
        lastError: String?
    ) {
        contentStateDao.upsert(
            WordBookContentStateEntity(
                bookId = bookId,
                targetVersion = targetVersion,
                localVersion = localVersion,
                status = status,
                downloadedWords = downloadedWords,
                totalWords = totalWords,
                packageSha256 = packageSha256,
                lastError = lastError,
                updatedAtMs = System.currentTimeMillis()
            )
        )
    }

    private suspend fun syncWordStates(
        bookId: Long,
        pageSize: Int,
        remoteUserSyncDataSource: RemoteUserSyncDataSource,
        wordLearningStateSnapshotStore: WordLearningStateSnapshotStore,
        learningSyncStatePort: LearningSyncStatePort
    ) {
        if (learningSyncStatePort.hasPendingLearningEvents()) {
            return
        }
        val states = mutableListOf<WordLearningState>()
        var page = 0
        var loaded = 0
        while (true) {
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

    private fun com.chen.memorizewords.data.wordbook.remoteapi.dto.wordstate.WordStateDto.toDomainState(
        fallbackBookId: Long
    ): WordLearningState {
        return WordLearningState(
            wordId = wordId,
            bookId = if (bookId > 0L) bookId else fallbackBookId,
            totalLearnCount = totalLearnCount,
            lastLearnedAtMs = lastLearnedAtMs,
            nextReviewAtMs = nextReviewAtMs,
            masteryLevel = masteryLevel,
            userStatus = userStatus,
            repetition = repetition,
            interval = interval,
            efactor = efactor
        )
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

    @SuppressLint("MissingPermission")
    private fun notifyIfAllowed(notificationId: Int, notification: Notification) {
        if (!canPostNotifications()) return
        try {
            NotificationManagerCompat.from(applicationContext).notify(notificationId, notification)
        } catch (_: SecurityException) {
            // Permission can still be revoked between the preflight check and notify().
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
    fun remoteUserSyncDataSource(): RemoteUserSyncDataSource
    fun appDatabase(): WordBookDatabase
    fun wordBookDao(): WordBookDao
    fun wordBookSyncStateStore(): WordBookSyncStateStore
    fun wordLearningStateSnapshotStore(): WordLearningStateSnapshotStore
    fun learningSyncStatePort(): LearningSyncStatePort
    fun wordBookContentPackageImporter(): WordBookContentPackageImporter
    fun wordBookContentStateDao(): WordBookContentStateDao
}

private data class FullRefreshImportContext(
    val packageSha256: String,
    val importResult: WordBookPackageImportResult
)

private const val DEFAULT_PAGE_SIZE = 50
