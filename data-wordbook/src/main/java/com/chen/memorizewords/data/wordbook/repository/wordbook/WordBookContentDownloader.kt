package com.chen.memorizewords.data.wordbook.repository.wordbook

import androidx.room.withTransaction
import com.chen.memorizewords.data.wordbook.local.WordBookDatabase
import com.chen.memorizewords.data.wordbook.local.room.model.wordbook.contentstate.WordBookContentStateDao
import com.chen.memorizewords.data.wordbook.local.room.model.wordbook.contentstate.WordBookContentStateEntity
import com.chen.memorizewords.data.wordbook.local.room.model.wordbook.contentstate.WordBookContentStatus
import com.chen.memorizewords.data.wordbook.local.room.model.wordbook.wordbook.toEntity
import com.chen.memorizewords.data.wordbook.local.room.model.wordbook.wordbook.toDomain
import com.chen.memorizewords.data.wordbook.local.room.wordbook.WordBookSyncStateStore
import com.chen.memorizewords.domain.wordbook.model.WordBook
import javax.inject.Inject
import kotlinx.coroutines.CancellationException

class WordBookContentDownloader @Inject constructor(
    private val database: WordBookDatabase,
    private val packageImporter: WordBookContentPackageImporter,
    private val contentStateDao: WordBookContentStateDao,
    private val syncStateStore: WordBookSyncStateStore
) {
    suspend fun ensureContentReady(
        book: WordBook,
        forceRefresh: Boolean = false,
        progress: suspend (WordBookContentDownloadProgress) -> Unit = {}
    ): WordBookContentDownloadResult {
        require(book.id > 0L) { "book.id must be positive" }

        database.wordBookDao().insertWordBook(book.toEntity())
        return downloadContent(
            bookId = book.id,
            expectedTotal = book.totalWords,
            targetVersion = book.contentVersion.coerceAtLeast(0L),
            forceRefresh = forceRefresh,
            progress = progress
        )
    }

    suspend fun downloadContent(
        bookId: Long,
        expectedTotal: Int,
        targetVersion: Long = 0L,
        forceRefresh: Boolean = false,
        progress: suspend (WordBookContentDownloadProgress) -> Unit = {}
    ): WordBookContentDownloadResult {
        require(bookId > 0L) { "bookId must be positive" }
        val localBook = database.wordBookDao().getWordBookById(bookId)?.toDomain()
        val target = targetVersion.coerceAtLeast(localBook?.contentVersion ?: 0L)
        var currentCount = database.wordBookItemDao().getWordCountByWordBookId(bookId)
        val existingState = contentStateDao.get(bookId)
        if (
            !forceRefresh &&
            existingState?.status == WordBookContentStatus.READY &&
            existingState.localVersion == target &&
            expectedTotal > 0 &&
            currentCount >= expectedTotal
        ) {
            return WordBookContentDownloadResult(
                bookId = bookId,
                downloadedWords = currentCount,
                totalWords = expectedTotal
            )
        }

        if (forceRefresh) {
            database.withTransaction {
                database.wordBookItemDao().deleteByBookId(bookId)
            }
            currentCount = 0
        }

        markState(
            bookId = bookId,
            targetVersion = target,
            localVersion = existingState?.localVersion ?: 0L,
            status = WordBookContentStatus.DOWNLOADING,
            downloadedWords = currentCount,
            totalWords = expectedTotal,
            packageSha256 = localBook?.contentPackage?.sha256,
            lastError = null
        )

        try {
            return importPackage(
                book = localBook ?: throw WordBookPackageValidationException("word book metadata missing"),
                targetVersion = target,
                forceRefresh = forceRefresh,
                progress = progress
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Exception) {
            markState(
                bookId = bookId,
                targetVersion = target,
                localVersion = existingState?.localVersion ?: 0L,
                status = WordBookContentStatus.FAILED,
                downloadedWords = database.wordBookItemDao().getWordCountByWordBookId(bookId),
                totalWords = expectedTotal,
                packageSha256 = localBook?.contentPackage?.sha256,
                lastError = throwable.message
            )
            throw throwable
        }
    }

    private suspend fun importPackage(
        book: WordBook,
        targetVersion: Long,
        forceRefresh: Boolean,
        progress: suspend (WordBookContentDownloadProgress) -> Unit
    ): WordBookContentDownloadResult {
        val contentPackage = book.contentPackage
            ?: throw WordBookPackageValidationException("content package missing")
        if (contentPackage.url.isBlank()) {
            throw WordBookPackageValidationException("content package url missing")
        }
        if (contentPackage.sha256.isBlank()) {
            throw WordBookPackageValidationException("content package sha256 missing")
        }
        if (targetVersion > 0L && contentPackage.contentVersion != targetVersion) {
            throw WordBookPackageValidationException(
                "content package version mismatch: expected=$targetVersion, actual=${contentPackage.contentVersion}"
            )
        }
        val result = packageImporter.importPackage(
            book = book,
            contentPackage = contentPackage,
            beforeImport = {
                database.withTransaction {
                    if (forceRefresh || targetVersion > 0L) {
                        database.wordBookItemDao().deleteByBookId(book.id)
                    }
                }
            }
        ) { downloaded, total ->
            markState(
                bookId = book.id,
                targetVersion = targetVersion,
                localVersion = 0L,
                status = WordBookContentStatus.DOWNLOADING,
                downloadedWords = downloaded,
                totalWords = total,
                packageSha256 = contentPackage.sha256,
                lastError = null
            )
            progress(
                WordBookContentDownloadProgress(
                    bookId = book.id,
                    downloadedWords = downloaded,
                    totalWords = total
                )
            )
        }
        if (targetVersion > 0L) {
            syncStateStore.setLocalVersion(book.id, targetVersion)
        }
        markState(
            bookId = book.id,
            targetVersion = targetVersion,
            localVersion = targetVersion,
            status = WordBookContentStatus.READY,
            downloadedWords = result.importedWords,
            totalWords = result.totalWords,
            packageSha256 = contentPackage.sha256,
            lastError = null
        )
        return WordBookContentDownloadResult(
            bookId = book.id,
            downloadedWords = result.importedWords,
            totalWords = result.totalWords
        )
    }

    private suspend fun markState(
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
}

data class WordBookContentDownloadProgress(
    val bookId: Long,
    val downloadedWords: Int,
    val totalWords: Int
) {
    val progressPercent: Int = toProgress(downloadedWords, totalWords)
}

data class WordBookContentDownloadResult(
    val bookId: Long,
    val downloadedWords: Int,
    val totalWords: Int
) {
    val progressPercent: Int = toProgress(downloadedWords, totalWords)
}

internal fun toProgress(downloadedCount: Int, total: Int): Int {
    if (total <= 0) return 0
    return ((downloadedCount * 100) / total).coerceIn(0, 100)
}
