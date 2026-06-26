package com.chen.memorizewords.data.wordbook.repository

import com.chen.memorizewords.data.wordbook.local.room.model.study.progress.word.WordLearningStateDao
import com.chen.memorizewords.data.wordbook.local.room.model.study.progress.wordbook.WordBookProgressDao
import com.chen.memorizewords.data.wordbook.local.room.model.study.progress.wordbook.WordBookProgressEntity
import com.chen.memorizewords.data.wordbook.local.room.model.wordbook.current.CurrentWordBookSelectionDao
import com.chen.memorizewords.data.wordbook.local.room.model.wordbook.current.CurrentWordBookSelectionEntity
import com.chen.memorizewords.data.wordbook.local.room.model.wordbook.wordbook.WordBookDao
import com.chen.memorizewords.data.wordbook.local.room.model.wordbook.wordbook.toDomain
import com.chen.memorizewords.data.wordbook.local.room.model.wordbook.words.BookWordItemDao
import com.chen.memorizewords.data.wordbook.local.room.model.wordbook.words.WordListRowProjection
import com.chen.memorizewords.data.wordbook.local.room.model.words.word.WordDao
import com.chen.memorizewords.data.wordbook.local.room.model.words.word.toDomain
import com.chen.memorizewords.domain.sync.OutboxTopic
import com.chen.memorizewords.domain.sync.SyncOperation
import com.chen.memorizewords.domain.sync.SyncOutboxWriter
import com.chen.memorizewords.domain.sync.WordBookProgressSyncPayload
import com.chen.memorizewords.domain.sync.WordBookSelectionSyncPayload
import com.chen.memorizewords.core.common.paging.PageSlice
import com.chen.memorizewords.domain.wordbook.model.WordBook
import com.chen.memorizewords.domain.wordbook.model.WordBookInfo
import com.chen.memorizewords.domain.wordbook.model.WordListQuery
import com.chen.memorizewords.domain.word.model.WordListRow
import com.chen.memorizewords.domain.word.model.enums.PartOfSpeech
import com.chen.memorizewords.domain.word.model.enums.WordFilter
import com.chen.memorizewords.domain.word.model.word.Word
import com.chen.memorizewords.domain.wordbook.repository.CurrentWordBookLocalStatePort
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
    private val myWordBookRemoteRemover: MyWordBookRemoteRemover,
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
                myWordBookRemoteRemover.removeMyWordBook(bookId).getOrThrow()
                transactionRunner.runInTransaction {
                    ensureDeletableMyWordBook(bookId)
                    wordBookDao.deleteByIds(listOf(bookId))
                }
                wordBookWorkCanceller.cancel(bookId)
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

    override suspend fun getWordRowsPage(query: WordListQuery): PageSlice<WordListRow> {
        val safePageIndex = query.pageIndex.coerceAtLeast(0)
        val safePageSize = query.pageSize.coerceAtLeast(1)
        val offset = safePageIndex * safePageSize
        val rows = loadWordRows(
            bookId = query.wordBookId,
            filter = query.filter,
            limit = safePageSize + 1,
            offset = offset
        )
        val hasNext = rows.size > safePageSize
        return PageSlice(
            items = rows.take(safePageSize).map { it.toDomain() },
            hasNext = hasNext
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
        limit: Int,
        offset: Int
    ): List<WordListRowProjection> {
        return when (filter) {
            WordFilter.ALL -> bookWordsDao.getWordListRowsPageAll(bookId, limit, offset)
            WordFilter.MASTERED -> bookWordsDao.getWordListRowsPageMastered(
                bookId = bookId,
                limit = limit,
                offset = offset,
                masteredLevel = MASTERED_LEVEL
            )

            WordFilter.LEARNED -> bookWordsDao.getWordListRowsPageLearned(
                bookId = bookId,
                limit = limit,
                offset = offset,
                masteredLevel = MASTERED_LEVEL
            )

            WordFilter.TO_LEARN -> bookWordsDao.getWordListRowsPageToLearn(bookId, limit, offset)
        }
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

    private fun WordListRowProjection.toDomain(): WordListRow {
        return WordListRow(
            wordId = wordId,
            word = word,
            phonetic = phonetic,
            partOfSpeech = PartOfSpeech.fromString(partOfSpeech),
            meanings = meanings,
            masteryLevel = masteryLevel
        )
    }

    private companion object {
        const val MASTERED_LEVEL = 5
    }
}
