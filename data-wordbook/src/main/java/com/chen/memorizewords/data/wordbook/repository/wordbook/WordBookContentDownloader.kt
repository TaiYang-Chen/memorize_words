package com.chen.memorizewords.data.wordbook.repository.wordbook

import androidx.room.withTransaction
import com.chen.memorizewords.data.wordbook.local.WordBookDatabase
import com.chen.memorizewords.data.wordbook.local.room.model.wordbook.wordbook.toEntity
import com.chen.memorizewords.data.wordbook.local.room.wordbook.WordBookSyncStateStore
import com.chen.memorizewords.data.wordbook.remote.wordbook.RemoteWordBookDataSource
import com.chen.memorizewords.domain.wordbook.model.WordBook
import javax.inject.Inject
import kotlin.math.min

class WordBookContentDownloader @Inject constructor(
    private val database: WordBookDatabase,
    private val remoteWordBookDataSource: RemoteWordBookDataSource,
    private val contentLocalStore: WordBookContentLocalStore,
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

        if (forceRefresh) {
            database.withTransaction {
                database.wordBookItemDao().deleteByBookId(bookId)
                database.wordLearningStateDao().deleteLearningWordByBookId(bookId)
            }
        }

        var downloadedCount = database.wordBookItemDao().getWordCountByWordBookId(bookId)
        var total = expectedTotal
        var pageSize = resolvePageSize(total)
        progress(
            WordBookContentDownloadProgress(
                bookId = bookId,
                downloadedWords = downloadedCount,
                totalWords = total
            )
        )

        if (total <= 0 || downloadedCount < total) {
            var page = downloadedCount / pageSize
            while (true) {
                val pageData = remoteWordBookDataSource
                    .getBookWords(bookId = bookId, page = page, count = pageSize)
                    .getOrThrow()

                if (total <= 0) {
                    total = if (pageData.total > 0) pageData.total.toInt() else expectedTotal
                    pageSize = resolvePageSize(total)
                }

                if (pageData.items.isEmpty()) break

                contentLocalStore.persistPage(bookId, pageData.items)
                downloadedCount = database.wordBookItemDao().getWordCountByWordBookId(bookId)
                progress(
                    WordBookContentDownloadProgress(
                        bookId = bookId,
                        downloadedWords = downloadedCount,
                        totalWords = total
                    )
                )

                if (total > 0 && downloadedCount >= total) break
                page++
            }
        }

        if (targetVersion > 0L) {
            syncStateStore.setLocalVersion(bookId, targetVersion)
        }

        return WordBookContentDownloadResult(
            bookId = bookId,
            downloadedWords = downloadedCount,
            totalWords = total
        )
    }

    private fun resolvePageSize(totalHint: Int): Int {
        if (totalHint > 0) {
            return min(DEFAULT_PAGE_SIZE, totalHint)
        }
        return DEFAULT_PAGE_SIZE
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

private const val DEFAULT_PAGE_SIZE = 50
