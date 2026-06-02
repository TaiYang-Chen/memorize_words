package com.chen.memorizewords.data.wordbook.repository

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.chen.memorizewords.data.wordbook.local.room.model.wordbook.wordbook.WordBookDao
import com.chen.memorizewords.data.wordbook.local.room.model.wordbook.wordbook.toEntity
import com.chen.memorizewords.data.wordbook.local.room.model.wordbook.words.BookWordItemDao
import com.chen.memorizewords.data.wordbook.remote.wordbook.RemoteWordBookDataSource
import com.chen.memorizewords.data.wordbook.repository.download.WordBookDownloadWorkConstants
import com.chen.memorizewords.data.wordbook.repository.download.WordBookDownloadWorker
import com.chen.memorizewords.core.common.paging.PageSlice
import com.chen.memorizewords.domain.wordbook.model.WordBook
import com.chen.memorizewords.domain.wordbook.model.shop.DownloadCommandResult
import com.chen.memorizewords.domain.wordbook.model.shop.DownloadState
import com.chen.memorizewords.domain.wordbook.model.shop.ShopBooksQuery
import com.chen.memorizewords.domain.wordbook.repository.shop.RemoteWordBookRepository
import com.chen.memorizewords.data.wordbook.remoteapi.dto.wordbook.WordBookDto
import com.tencent.mmkv.MMKV
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

class RemoteWordBookRepositoryImpl @Inject constructor(
    @ApplicationContext context: Context,
    private val remote: RemoteWordBookDataSource,
    private val wordBookDao: WordBookDao,
    private val bookWordItemDao: BookWordItemDao,
    private val mmkv: MMKV
) : RemoteWordBookRepository {

    private val appContext = context.applicationContext

    private fun workManagerOrNull(): WorkManager? {
        return runCatching { WorkManager.getInstance(appContext) }.getOrNull()
    }

    private val pausedBookIdsFlow = MutableStateFlow(loadPausedBookIds())

    override suspend fun getShopBooks(query: ShopBooksQuery): PageSlice<WordBook> {
        val dtoList = remote.getWordBooks().getOrThrow()
        val filteredBooks = dtoList
            .map { it.toShopDomain() }
            .filter { book -> matchesShopQuery(book, query) }
        val safePageIndex = query.pageIndex.coerceAtLeast(0)
        val safePageSize = query.pageSize.coerceAtLeast(1)
        val fromIndex = (safePageIndex * safePageSize).coerceAtMost(filteredBooks.size)
        val toIndex = (fromIndex + safePageSize).coerceAtMost(filteredBooks.size)
        return PageSlice(
            items = filteredBooks.subList(fromIndex, toIndex),
            hasNext = toIndex < filteredBooks.size
        )
    }

    override suspend fun getShopBookById(bookId: Long): WordBook? {
        if (bookId <= 0L) return null
        return remote.getWordBooks()
            .getOrThrow()
            .firstOrNull { it.id == bookId }
            ?.toShopDomain()
    }

    override fun observeDownloadStates(): Flow<Map<Long, DownloadState>> {
        val localBooksFlow = wordBookDao.getAllWordBooksFlow()
        val localCountFlow = bookWordItemDao.observeBookWordCounts()
            .map { list -> list.associate { it.bookId to it.wordCount } }
        val workStateFlow = workManagerOrNull()
            ?.getWorkInfosByTagFlow(WordBookDownloadWorkConstants.TAG_DOWNLOAD)
            ?.map { infos -> infos.toBookWorkStateMap() }
            ?: flowOf(emptyMap())

        return combine(localBooksFlow, localCountFlow, workStateFlow, pausedBookIdsFlow) {
                localBooks,
                countMap,
                workStates,
                pausedIds
            ->
            localBooks.associate { book ->
                val downloadedCount = countMap[book.id] ?: 0
                val workState = workStates[book.id]
                book.id to resolveShopDownloadState(
                    downloadedCount = downloadedCount,
                    totalWords = book.totalWords,
                    workState = workState,
                    isPaused = pausedIds.contains(book.id)
                )
            }
        }
    }

    override suspend fun downloadBook(
        book: WordBook,
        forceRefresh: Boolean,
        runInForeground: Boolean
    ): DownloadCommandResult {
        val workManager = workManagerOrNull()
        wordBookDao.insertWordBook(book.toEntity())
        markPaused(book.id, paused = false)

        val request = OneTimeWorkRequestBuilder<WordBookDownloadWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .setInputData(
                androidx.work.workDataOf(
                    WordBookDownloadWorkConstants.KEY_BOOK_ID to book.id,
                    WordBookDownloadWorkConstants.KEY_BOOK_TITLE to book.title,
                    WordBookDownloadWorkConstants.KEY_TOTAL_WORDS to book.totalWords,
                    WordBookDownloadWorkConstants.KEY_REPORT_MY_BOOK to true,
                    WordBookDownloadWorkConstants.KEY_FORCE_REFRESH to forceRefresh,
                    WordBookDownloadWorkConstants.KEY_TARGET_VERSION to resolveShopTargetVersion(book),
                    WordBookDownloadWorkConstants.KEY_RUN_IN_FOREGROUND to runInForeground
                )
            )
            .addTag(WordBookDownloadWorkConstants.TAG_DOWNLOAD)
            .addTag(WordBookDownloadWorkConstants.bookTag(book.id))
            .build()

        workManager?.enqueueUniqueWork(
            WordBookDownloadWorkConstants.uniqueWorkName(book.id),
            if (forceRefresh) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
            request
        )
        return DownloadCommandResult(message = "Download queued")
    }

    override suspend fun cancelDownload(bookId: Long) {
        markPaused(bookId, paused = true)
        workManagerOrNull()
            ?.cancelUniqueWork(WordBookDownloadWorkConstants.uniqueWorkName(bookId))
    }

    private fun loadPausedBookIds(): Set<Long> {
        val values = mmkv.decodeStringSet(
            WordBookDownloadWorkConstants.KEY_PAUSED_BOOK_IDS,
            emptySet()
        ) ?: emptySet()
        return values.mapNotNull { it.toLongOrNull() }.toSet()
    }

    private fun markPaused(bookId: Long, paused: Boolean) {
        pausedBookIdsFlow.update { current ->
            val updated = current.toMutableSet()
            if (paused) {
                updated.add(bookId)
            } else {
                updated.remove(bookId)
            }
            mmkv.encode(
                WordBookDownloadWorkConstants.KEY_PAUSED_BOOK_IDS,
                updated.map { it.toString() }.toSet()
            )
            updated
        }
    }

    private fun List<WorkInfo>.toBookWorkStateMap(): Map<Long, BookWorkState> {
        return this
            .mapNotNull { info ->
                val bookId = info.getBookId()
                if (bookId <= 0L) return@mapNotNull null
                bookId to info
            }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, infos) ->
                val activeInfo = infos.firstOrNull {
                    it.state == WorkInfo.State.RUNNING ||
                        it.state == WorkInfo.State.ENQUEUED ||
                        it.state == WorkInfo.State.BLOCKED
                }
                if (activeInfo != null) {
                    return@mapValues BookWorkState(
                        isActive = true,
                        progress = activeInfo.getProgressPercent(),
                        hasFailed = false,
                        errorMessage = null
                    )
                }

                val failedInfo = infos.firstOrNull { it.state == WorkInfo.State.FAILED }
                BookWorkState(
                    isActive = false,
                    progress = 0,
                    hasFailed = failedInfo != null,
                    errorMessage = failedInfo?.outputData?.getString(
                        WordBookDownloadWorkConstants.KEY_ERROR_MESSAGE
                    )
                )
            }
    }

    private fun WorkInfo.getBookId(): Long {
        val fromProgress = progress.getLong(WordBookDownloadWorkConstants.KEY_BOOK_ID, -1L)
        if (fromProgress > 0L) return fromProgress
        val fromOutput = outputData.getLong(WordBookDownloadWorkConstants.KEY_BOOK_ID, -1L)
        if (fromOutput > 0L) return fromOutput
        val tag = tags.firstOrNull { it.startsWith(WordBookDownloadWorkConstants.TAG_BOOK_PREFIX) }
            ?: return -1L
        return tag.removePrefix(WordBookDownloadWorkConstants.TAG_BOOK_PREFIX).toLongOrNull() ?: -1L
    }

    private fun WorkInfo.getProgressPercent(): Int {
        return progress.getInt(WordBookDownloadWorkConstants.KEY_PROGRESS, 0).coerceIn(0, 100)
    }

    private fun matchesShopQuery(book: WordBook, query: ShopBooksQuery): Boolean {
        val normalizedCategory = query.category.trim()
        val categoryMatch = normalizedCategory.isBlank() ||
            normalizedCategory == DEFAULT_CATEGORY ||
            book.category == normalizedCategory
        if (!categoryMatch) return false

        val keyword = query.keyword.trim()
        if (keyword.isBlank()) return true
        return book.title.contains(keyword, ignoreCase = true) ||
            book.description.contains(keyword, ignoreCase = true)
    }
}

internal data class BookWorkState(
    val isActive: Boolean,
    val progress: Int,
    val hasFailed: Boolean,
    val errorMessage: String?
)

internal fun WordBookDto.toShopDomain(): WordBook {
    return WordBook(
        id = id,
        title = title,
        category = category,
        imgUrl = imgUrl,
        description = description,
        totalWords = totalWords,
        contentVersion = contentVersion,
        isNew = isNew,
        isHot = isHot,
        isSelected = isSelected,
        isPublic = isPublic,
        createdByUserId = createdByUserId
    )
}

internal fun resolveShopDownloadState(
    downloadedCount: Int,
    totalWords: Int,
    workState: BookWorkState?,
    isPaused: Boolean
): DownloadState {
    val countProgress = toProgress(downloadedCount, totalWords)
    val isDownloaded = totalWords in 1..downloadedCount
    return when {
        workState?.isActive == true -> DownloadState.Downloading(
            maxOf(countProgress, workState.progress)
        )
        isPaused && downloadedCount > 0 -> DownloadState.Paused(countProgress)
        workState?.hasFailed == true -> DownloadState.Failed(workState.errorMessage ?: "娑撳娴囨径杈Е")
        isDownloaded -> DownloadState.Downloaded
        downloadedCount > 0 -> DownloadState.Paused(countProgress)
        else -> DownloadState.NotDownloaded
    }
}

internal fun resolveShopTargetVersion(book: WordBook): Long = book.contentVersion.coerceAtLeast(0L)

private fun toProgress(downloadedCount: Int, total: Int): Int {
    if (total <= 0) return 0
    return ((downloadedCount * 100) / total).coerceIn(0, 100)
}

private const val DEFAULT_CATEGORY = "全部"
