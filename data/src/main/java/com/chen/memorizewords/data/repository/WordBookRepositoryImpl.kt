package com.chen.memorizewords.data.repository

import androidx.room.withTransaction
import com.chen.memorizewords.data.local.room.AppDatabase
import com.chen.memorizewords.data.local.room.model.sync.SyncOutboxDao
import com.chen.memorizewords.data.local.room.model.study.progress.word.WordLearningStateDao
import com.chen.memorizewords.data.local.room.model.study.progress.wordbook.WordBookProgressDao
import com.chen.memorizewords.data.local.room.model.study.progress.wordbook.WordBookProgressEntity
import com.chen.memorizewords.data.local.room.model.wordbook.wordbook.WordBookDao
import com.chen.memorizewords.data.local.room.model.wordbook.wordbook.toDomain
import com.chen.memorizewords.data.local.room.model.wordbook.words.BookWordItemDao
import com.chen.memorizewords.data.local.room.model.wordbook.words.WordListRowProjection
import com.chen.memorizewords.data.local.room.model.words.word.WordDao
import com.chen.memorizewords.data.local.room.model.words.word.toDomain
import com.chen.memorizewords.data.repository.sync.SyncOutboxBizType
import com.chen.memorizewords.data.repository.sync.SyncOutboxOperation
import com.chen.memorizewords.data.repository.sync.SyncOutboxWorkScheduler
import com.chen.memorizewords.data.repository.sync.WordBookProgressSyncPayload
import com.chen.memorizewords.data.repository.sync.WordBookSelectionSyncPayload
import com.chen.memorizewords.data.repository.sync.syncOutboxEntity
import com.chen.memorizewords.domain.model.common.PageSlice
import com.chen.memorizewords.domain.model.wordbook.WordBook
import com.chen.memorizewords.domain.model.wordbook.WordBookInfo
import com.chen.memorizewords.domain.model.wordbook.WordListQuery
import com.chen.memorizewords.domain.model.words.WordListRow
import com.chen.memorizewords.domain.model.words.enums.PartOfSpeech
import com.chen.memorizewords.domain.model.words.enums.WordFilter
import com.chen.memorizewords.domain.model.words.word.Word
import com.chen.memorizewords.domain.repository.WordBookRepository
import com.google.gson.Gson
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class WordBookRepositoryImpl @Inject constructor(
    private val appDatabase: AppDatabase,
    private val wordLearningStateDao: WordLearningStateDao,
    private val wordBookProgressDao: WordBookProgressDao,
    private val bookWordsDao: BookWordItemDao,
    private val wordDao: WordDao,
    private val wordBookDao: WordBookDao,
    private val syncOutboxDao: SyncOutboxDao,
    private val syncOutboxWorkScheduler: SyncOutboxWorkScheduler,
    private val gson: Gson
) : WordBookRepository {

    override fun getMyWordBooksMinimalFlow(): Flow<List<WordBookInfo>> {
        return wordBookDao.getMyWordBooksFlow()
            .map { list ->
                list.map { bookEntity ->
                    WordBookInfo(
                        bookId = bookEntity.id,
                        title = bookEntity.title,
                        category = bookEntity.category,
                        imgUrl = bookEntity.imgUrl,
                        description = bookEntity.description,
                        totalWords = bookEntity.totalWords,
                        isSelected = bookEntity.isSelected,
                        studyDayCount = 0,
                        createdByUserId = bookEntity.createdByUserId
                    )
                }
            }
            .distinctUntilChanged()
    }

    override fun getCurrentWordBookMinimalFlow(): Flow<WordBookInfo?> {
        return wordBookDao.getCurrentWordBookFlow()
            .map { bookEntity ->
                bookEntity?.let {
                    WordBookInfo(
                        bookId = it.id,
                        title = it.title,
                        category = it.category,
                        imgUrl = it.imgUrl,
                        description = it.description,
                        totalWords = it.totalWords,
                        studyDayCount = 0,
                        createdByUserId = it.createdByUserId
                    )
                }
            }
            .distinctUntilChanged()
    }

    override suspend fun setCurrentWordBook(bookId: Long) {
        withContext(Dispatchers.IO) {
            appDatabase.withTransaction {
                wordBookDao.setCurrentWordBook(bookId)
                syncOutboxDao.upsert(
                    syncOutboxEntity(
                        bizType = SyncOutboxBizType.WORD_BOOK_SELECTION,
                        bizKey = "word_book_selection",
                        operation = SyncOutboxOperation.UPSERT,
                        payload = gson.toJson(WordBookSelectionSyncPayload(bookId = bookId))
                    )
                )
            }
            syncOutboxWorkScheduler.scheduleDrain()
        }
    }

    override suspend fun getCurrentWordBook(): WordBook? {
        return wordBookDao.getCurrentWordBook()?.toDomain()
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
                        learnedCount = 0,
                        masteredCount = 0,
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
            appDatabase.withTransaction {
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
        appDatabase.withTransaction {
            syncOutboxDao.upsert(
                syncOutboxEntity(
                    bizType = SyncOutboxBizType.WORD_BOOK_PROGRESS,
                    bizKey = "word_book_progress:$bookId",
                    operation = SyncOutboxOperation.UPSERT,
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
                            lastStudyDate = progress.lastStudyDate
                        )
                    )
                )
            )
        }
        syncOutboxWorkScheduler.scheduleDrain()
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
