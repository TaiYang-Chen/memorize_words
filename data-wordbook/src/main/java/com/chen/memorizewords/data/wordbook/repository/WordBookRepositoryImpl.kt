package com.chen.memorizewords.data.wordbook.repository

import com.chen.memorizewords.data.wordbook.local.room.model.study.progress.word.WordLearningStateDao
import com.chen.memorizewords.data.wordbook.local.room.model.study.progress.wordbook.WordBookProgressDao
import com.chen.memorizewords.data.wordbook.local.room.model.study.progress.wordbook.WordBookProgressEntity
import com.chen.memorizewords.data.wordbook.local.room.model.wordbook.current.CurrentWordBookSelectionDao
import com.chen.memorizewords.data.wordbook.local.room.model.wordbook.current.CurrentWordBookSelectionEntity
import com.chen.memorizewords.data.wordbook.local.room.model.wordbook.wordbook.WordBookDao
import com.chen.memorizewords.data.wordbook.local.room.model.wordbook.wordbook.toDomain
import com.chen.memorizewords.data.wordbook.local.room.model.wordbook.wordbook.toEntity
import com.chen.memorizewords.data.wordbook.local.room.model.wordbook.words.BookWordItemDao
import com.chen.memorizewords.data.wordbook.local.room.model.wordbook.words.WordListRowProjection
import com.chen.memorizewords.data.wordbook.local.room.model.words.word.WordDao
import com.chen.memorizewords.data.wordbook.local.room.model.words.word.toDomain
import com.chen.memorizewords.data.wordbook.remote.datasync.RemoteUserSyncDataSource
import com.chen.memorizewords.data.wordbook.repository.wordbook.WordBookContentDownloader
import com.chen.memorizewords.domain.sync.OutboxTopic
import com.chen.memorizewords.domain.sync.SyncOperation
import com.chen.memorizewords.domain.sync.SyncOutboxWriter
import com.chen.memorizewords.domain.sync.WordBookDeleteSyncPayload
import com.chen.memorizewords.domain.sync.WordBookProgressSyncPayload
import com.chen.memorizewords.domain.sync.WordBookSelectionSyncPayload
import com.chen.memorizewords.core.common.paging.PageSlice
import com.chen.memorizewords.domain.wordbook.model.WordBook
import com.chen.memorizewords.domain.wordbook.model.WordBookInfo
import com.chen.memorizewords.domain.wordbook.model.WordListQuery
import com.chen.memorizewords.domain.wordbook.model.WordListSummary
import com.chen.memorizewords.domain.word.model.WordListRow
import com.chen.memorizewords.domain.word.model.enums.PartOfSpeech
import com.chen.memorizewords.domain.word.model.enums.WordFilter
import com.chen.memorizewords.domain.word.model.enums.WordLearningStatus
import com.chen.memorizewords.domain.word.model.word.Word
import com.chen.memorizewords.domain.study.repository.word.FavoritesRepository
import com.chen.memorizewords.domain.wordbook.repository.CurrentWordBookLocalStatePort
import com.chen.memorizewords.domain.wordbook.repository.WordOrderType
import com.chen.memorizewords.domain.wordbook.repository.WordBookRepository
import com.google.gson.Gson
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class WordBookRepositoryImpl @Inject constructor(
    private val transactionRunner: WordBookTransactionRunner,
    private val wordLearningStateDao: WordLearningStateDao,
    private val wordBookProgressDao: WordBookProgressDao,
    private val bookWordsDao: BookWordItemDao,
    private val wordDao: WordDao,
    private val wordBookDao: WordBookDao,
    private val currentWordBookSelectionDao: CurrentWordBookSelectionDao,
    private val favoritesRepository: FavoritesRepository,
    private val myWordBookRemoteRemover: MyWordBookRemoteRemover,
    private val remoteUserSyncDataSource: RemoteUserSyncDataSource,
    private val wordBookContentDownloader: WordBookContentDownloader,
    private val wordBookWorkCanceller: WordBookWorkCanceller,
    private val SyncOutboxWriter: SyncOutboxWriter,
    private val gson: Gson
) : WordBookRepository, CurrentWordBookLocalStatePort {

    override fun getMyWordBooksMinimalFlow(): Flow<List<WordBookInfo>> {
        return combine(
            wordBookDao.getMyWordBooksFlow(),
            currentWordBookSelectionDao.observeById()
        ) { books, selection ->
            val selectedBookId = selection?.bookId
            books.map { bookEntity ->
                WordBookInfo(
                    bookId = bookEntity.id,
                    title = bookEntity.title,
                    category = bookEntity.category,
                    imgUrl = bookEntity.imgUrl,
                    description = bookEntity.description,
                    totalWords = bookEntity.totalWords,
                    isSelected = bookEntity.id == selectedBookId,
                    studyDayCount = 0,
                    createdByUserId = bookEntity.createdByUserId
                )
            }
        }.distinctUntilChanged()
    }

    override fun getCurrentWordBookMinimalFlow(): Flow<WordBookInfo?> {
        return combine(
            wordBookDao.getAllWordBooksFlow(),
            currentWordBookSelectionDao.observeById()
        ) { books, selection ->
            val selected = selection ?: return@combine null
            books.firstOrNull { it.id == selected.bookId }?.let {
                WordBookInfo(
                    bookId = it.id,
                    title = it.title,
                    category = it.category,
                    imgUrl = it.imgUrl,
                    description = it.description,
                    totalWords = it.totalWords,
                    studyDayCount = 0,
                    isSelected = true,
                    createdByUserId = it.createdByUserId
                )
            }
        }.distinctUntilChanged()
    }

    override suspend fun setCurrentWordBook(bookId: Long) {
        withContext(Dispatchers.IO) {
            if (!wordBookDao.exists(bookId)) return@withContext
            transactionRunner.runInTransaction {
                currentWordBookSelectionDao.upsert(CurrentWordBookSelectionEntity(bookId = bookId))
                SyncOutboxWriter.enqueueLatest(
                    bizType = OutboxTopic.WORD_BOOK_SELECTION,
                    bizKey = "word_book_selection",
                    operation = SyncOperation.UPSERT,
                    payload = gson.toJson(WordBookSelectionSyncPayload(bookId = bookId))
                )
            }
        }
    }

    override suspend fun deleteMyWordBook(bookId: Long): Result<Unit> {
        return withContext(Dispatchers.IO) {
            runCatching {
                require(bookId > 0L) { "bookId invalid" }
                ensureDeletableMyWordBook(bookId)
                transactionRunner.runInTransaction {
                    ensureDeletableMyWordBook(bookId)
                    wordBookDao.deleteByIds(listOf(bookId))
                    SyncOutboxWriter.enqueueLatest(
                        bizType = OutboxTopic.WORD_BOOK_DELETE,
                        bizKey = "word_book_delete:$bookId",
                        operation = SyncOperation.DELETE,
                        payload = gson.toJson(WordBookDeleteSyncPayload(bookId = bookId))
                    )
                }
                wordBookWorkCanceller.cancel(bookId)
            }
        }
    }

    override suspend fun createMyWordBook(
        title: String,
        category: String,
        description: String,
        words: List<String>
    ): Result<WordBookInfo> {
        return withContext(Dispatchers.IO) {
            runCatching {
                val dto = remoteUserSyncDataSource.createMyWordBook(
                    title = title,
                    category = category,
                    description = description,
                    words = words
                ).getOrThrow()
                val entity = dto.toEntity()
                wordBookDao.insertWordBook(entity)
                runCatching {
                    wordBookContentDownloader.downloadContent(
                        bookId = entity.id,
                        expectedTotal = entity.totalWords,
                        targetVersion = entity.contentVersion,
                        forceRefresh = true
                    )
                }
                WordBookInfo(
                    bookId = entity.id,
                    title = entity.title,
                    category = entity.category,
                    imgUrl = entity.imgUrl,
                    description = entity.description,
                    totalWords = entity.totalWords,
                    isSelected = false,
                    studyDayCount = 0,
                    createdByUserId = entity.createdByUserId
                )
            }
        }
    }

    override suspend fun getCurrentWordBook(): WordBook? {
        val selection = currentWordBookSelectionDao.getById() ?: return null
        val book = wordBookDao.getWordBookById(selection.bookId) ?: return null
        return book.toDomain(isSelected = true)
    }

    override suspend fun getBookNameById(bookId: Long): String? {
        return wordBookDao.getBookNameById(bookId)
    }

    override suspend fun getWordListSummary(wordBookId: Long, now: Long): WordListSummary {
        val favoriteIds = safeFavoriteIds()
        val summary = bookWordsDao.getWordListSummary(
            bookId = wordBookId,
            favoriteWordIds = favoriteIds,
            masteredLevel = MASTERED_LEVEL,
            now = now
        )
        return WordListSummary(
            totalCount = summary.totalCount,
            learnedCount = summary.learnedCount ?: 0,
            masteredCount = summary.masteredCount ?: 0,
            reviewDueCount = summary.reviewDueCount ?: 0,
            favoriteCount = summary.favoriteCount ?: 0
        )
    }

    override suspend fun getWordRowsPage(query: WordListQuery): PageSlice<WordListRow> {
        val safePageIndex = query.pageIndex.coerceAtLeast(0)
        val safePageSize = query.pageSize.coerceAtLeast(1)
        val offset = safePageIndex * safePageSize
        val favoriteIds = safeFavoriteIds()
        val favoriteIdSet = favoriteIds.toSet()
        val rows = loadWordRows(
            bookId = query.wordBookId,
            filter = query.filter,
            keyword = query.normalizedKeyword,
            sortType = query.sortType.name,
            favoriteWordIds = favoriteIds,
            now = query.now,
            limit = safePageSize + 1,
            offset = offset
        )
        val hasNext = rows.size > safePageSize
        return PageSlice(
            items = rows.take(safePageSize).map { it.toDomain(favoriteIdSet, query.now) },
            hasNext = hasNext
        )
    }

    override suspend fun getWordRowIds(query: WordListQuery, limit: Int): List<Long> {
        if (limit <= 0) return emptyList()
        val favoriteIds = safeFavoriteIds()
        return bookWordsDao.getWordListRowIds(
            bookId = query.wordBookId,
            keyword = query.normalizedKeyword,
            filter = query.filter.name,
            sortType = query.sortType.name,
            favoriteOnly = if (query.filter == WordFilter.FAVORITE) 1 else 0,
            favoriteWordIds = favoriteIds,
            masteredLevel = MASTERED_LEVEL,
            now = query.now,
            limit = limit
        )
    }

    override suspend fun getWordIdsPage(
        wordBookId: Long,
        pageIndex: Int,
        pageSize: Int
    ): List<Long> {
        val offset = pageIndex * pageSize
        return wordBookDao.getWordIdsPage(
            wordBookId = wordBookId,
            limit = pageSize,
            offset = offset
        )
    }

    override suspend fun getAllUnlearnedWordsForBook(bookId: Long): List<Word> {
        if (!wordBookDao.exists(bookId)) {
            throw NoSuchElementException("Word book with id=$bookId not found")
        }
        val candidateIds = bookWordsDao.getUnlearnedWordIdsForBook(bookId)
        val words = wordDao.getWithRelationsByIds(candidateIds)
        return words.map { it.toDomain() }
    }

    override suspend fun getUnlearnedWordIdsForBook(
        bookId: Long,
        count: Int,
        orderType: WordOrderType,
        excludeIds: Set<Long>
    ): List<Long> {
        if (count <= 0) return emptyList()
        if (!wordBookDao.exists(bookId)) {
            throw NoSuchElementException("Word book with id=$bookId not found")
        }
        val limit = count.coerceAtLeast(1)
        val excluded = excludeIds.toList()
        return if (excluded.isEmpty()) {
            when (orderType) {
                WordOrderType.RANDOM -> bookWordsDao.getRandomUnlearnedWordIdsForBook(bookId, limit)
                WordOrderType.ALPHABETIC_ASC -> bookWordsDao.getUnlearnedWordIdsAlphabeticAsc(bookId, limit)
                WordOrderType.ALPHABETIC_DESC -> bookWordsDao.getUnlearnedWordIdsAlphabeticDesc(bookId, limit)
                WordOrderType.LENGTH_ASC -> bookWordsDao.getUnlearnedWordIdsLengthAsc(bookId, limit)
                WordOrderType.LENGTH_DESC -> bookWordsDao.getUnlearnedWordIdsLengthDesc(bookId, limit)
            }
        } else {
            when (orderType) {
                WordOrderType.RANDOM -> bookWordsDao.getRandomUnlearnedWordIdsForBookExcluding(bookId, limit, excluded)
                WordOrderType.ALPHABETIC_ASC -> bookWordsDao.getUnlearnedWordIdsAlphabeticAscExcluding(bookId, limit, excluded)
                WordOrderType.ALPHABETIC_DESC -> bookWordsDao.getUnlearnedWordIdsAlphabeticDescExcluding(bookId, limit, excluded)
                WordOrderType.LENGTH_ASC -> bookWordsDao.getUnlearnedWordIdsLengthAscExcluding(bookId, limit, excluded)
                WordOrderType.LENGTH_DESC -> bookWordsDao.getUnlearnedWordIdsLengthDescExcluding(bookId, limit, excluded)
            }
        }
    }

    override suspend fun updateBookStudyDay(bookId: Long, today: String) {
        withContext(Dispatchers.IO) {
            val book = wordBookDao.getWordBookById(bookId) ?: return@withContext
            val progress = wordBookProgressDao.getProgress(bookId)
            if (progress == null) {
                wordBookProgressDao.upsert(
                    WordBookProgressEntity(
                        wordBookId = bookId,
                        correctCount = 0,
                        wrongCount = 0,
                        studyDayCount = 1,
                        lastStudyDate = today
                    )
                )
                enqueueWordBookProgressOutbox(bookId, book.title, book.totalWords)
                return@withContext
            }

            if (progress.lastStudyDate == today) return@withContext

            wordBookProgressDao.upsert(
                progress.copy(
                    studyDayCount = progress.studyDayCount + 1,
                    lastStudyDate = today
                )
            )
            enqueueWordBookProgressOutbox(bookId, book.title, book.totalWords)
        }
    }

    override suspend fun recordAnswerResult(bookId: Long, isCorrect: Boolean, today: String) {
        withContext(Dispatchers.IO) {
            if (bookId <= 0L) return@withContext
            val book = wordBookDao.getWordBookById(bookId) ?: return@withContext
            transactionRunner.runInTransaction {
                wordBookProgressDao.ensureProgressRow(bookId = bookId)
                wordBookProgressDao.incrementAnswerStats(
                    bookId = bookId,
                    isCorrect = if (isCorrect) 1 else 0,
                    today = today
                )
            }
            enqueueWordBookProgressOutbox(bookId, book.title, book.totalWords)
        }
    }

    override suspend fun upsertBookAndSelectionFromRemote(book: WordBook?) {
        withContext(Dispatchers.IO) {
            if (book == null || book.id <= 0L) {
                currentWordBookSelectionDao.deleteAll()
                return@withContext
            }
            transactionRunner.runInTransaction {
                wordBookDao.insertWordBook(book.toEntity())
                currentWordBookSelectionDao.upsert(CurrentWordBookSelectionEntity(bookId = book.id))
            }
        }
    }

    override suspend fun overwriteFromRemote(bookId: Long?) {
        withContext(Dispatchers.IO) {
            when {
                bookId == null || bookId <= 0L -> currentWordBookSelectionDao.deleteAll()
                wordBookDao.exists(bookId) -> {
                    currentWordBookSelectionDao.upsert(CurrentWordBookSelectionEntity(bookId = bookId))
                }

                else -> currentWordBookSelectionDao.deleteAll()
            }
        }
    }

    override suspend fun clearLocalState() {
        withContext(Dispatchers.IO) {
            currentWordBookSelectionDao.deleteAll()
        }
    }

    private suspend fun ensureDeletableMyWordBook(bookId: Long) {
        if (!wordBookDao.exists(bookId)) {
            throw NoSuchElementException("Word book with id=$bookId not found")
        }
        if (currentWordBookSelectionDao.getById()?.bookId == bookId) {
            throw IllegalStateException("current wordbook cannot be deleted")
        }
    }

    private suspend fun loadWordRows(
        bookId: Long,
        filter: WordFilter,
        keyword: String,
        sortType: String,
        favoriteWordIds: List<Long>,
        now: Long,
        limit: Int,
        offset: Int
    ): List<WordListRowProjection> {
        return bookWordsDao.getWordListRowsPage(
            bookId = bookId,
            keyword = keyword,
            filter = filter.name,
            sortType = sortType,
            favoriteOnly = if (filter == WordFilter.FAVORITE) 1 else 0,
            favoriteWordIds = favoriteWordIds,
            masteredLevel = MASTERED_LEVEL,
            now = now,
            limit = limit,
            offset = offset
        )
    }

    private suspend fun enqueueWordBookProgressOutbox(
        bookId: Long,
        bookName: String,
        totalWords: Int
    ) {
        val progress = wordBookProgressDao.getProgress(bookId) ?: return
        val learnedCount = wordLearningStateDao.getLearnedCountByBookId(bookId)
        val masteredCount = wordLearningStateDao.getMasteredCountByBookId(bookId)
        transactionRunner.runInTransaction {
            SyncOutboxWriter.enqueueLatest(
                bizType = OutboxTopic.WORD_BOOK_PROGRESS,
                bizKey = "word_book_progress:$bookId",
                operation = SyncOperation.UPSERT,
                payload = gson.toJson(
                    WordBookProgressSyncPayload(
                        bookId = bookId,
                        bookName = bookName,
                        learnedCount = learnedCount,
                        masteredCount = masteredCount,
                        totalCount = totalWords,
                        correctCount = progress.correctCount,
                        wrongCount = progress.wrongCount,
                        studyDayCount = progress.studyDayCount,
                        lastStudyDate = progress.lastStudyDate.orEmpty()
                    )
                )
            )
        }
    }

    private suspend fun safeFavoriteIds(): List<Long> {
        return favoritesRepository.getAllFavoriteWordIds().ifEmpty { listOf(NO_FAVORITE_WORD_ID) }
    }

    private fun WordListRowProjection.toDomain(favoriteIds: Set<Long>, now: Long): WordListRow {
        val status = resolveLearningStatus(now)
        return WordListRow(
            wordId = wordId,
            word = word,
            phonetic = phonetic,
            partOfSpeech = PartOfSpeech.fromString(partOfSpeech),
            meanings = meanings,
            masteryLevel = masteryLevel,
            isFavorite = wordId in favoriteIds,
            learningStatus = status,
            totalLearnCount = totalLearnCount,
            lastLearnTime = lastLearnTime,
            nextReviewTime = nextReviewTime
        )
    }

    private fun WordListRowProjection.resolveLearningStatus(now: Long): WordLearningStatus {
        val mastered = masteryLevel >= MASTERED_LEVEL || userStatus == USER_STATUS_MASTERED
        if (mastered) return WordLearningStatus.MASTERED
        val learned = totalLearnCount > 0 || masteryLevel > 0
        val reviewDue = learned && nextReviewTime > 0 && nextReviewTime <= now
        return when {
            reviewDue -> WordLearningStatus.REVIEW_DUE
            learned -> WordLearningStatus.LEARNED
            else -> WordLearningStatus.TO_LEARN
        }
    }

    private companion object {
        const val MASTERED_LEVEL = 5
        const val USER_STATUS_MASTERED = 1
        const val NO_FAVORITE_WORD_ID = -1L
    }
}
