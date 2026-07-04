package com.chen.memorizewords.domain.wordbook.repository
import com.chen.memorizewords.core.common.paging.PageSlice
import com.chen.memorizewords.domain.wordbook.model.WordBook
import com.chen.memorizewords.domain.wordbook.model.WordBookInfo
import com.chen.memorizewords.domain.wordbook.model.WordListQuery
import com.chen.memorizewords.domain.wordbook.model.WordListSummary
import com.chen.memorizewords.domain.word.model.WordListRow
import com.chen.memorizewords.domain.word.model.word.Word
import kotlinx.coroutines.flow.Flow

interface WordBookRepository {
    fun getMyWordBooksMinimalFlow(): Flow<List<WordBookInfo>>
    fun getCurrentWordBookMinimalFlow(): Flow<WordBookInfo?>
    suspend fun setCurrentWordBook(bookId: Long)
    suspend fun deleteMyWordBook(bookId: Long): Result<Unit>
    suspend fun getCurrentWordBook(): WordBook?

    suspend fun getBookNameById(bookId: Long): String?
    suspend fun getWordListSummary(wordBookId: Long, now: Long = System.currentTimeMillis()): WordListSummary
    suspend fun getWordRowsPage(query: WordListQuery): PageSlice<WordListRow>
    suspend fun getWordRowIds(query: WordListQuery, limit: Int): List<Long>
    suspend fun getWordIdsPage(wordBookId: Long, pageIndex: Int, pageSize: Int): List<Long>
    suspend fun getAllUnlearnedWordsForBook(bookId: Long): List<Word>
    suspend fun getUnlearnedWordIdsForBook(
        bookId: Long,
        count: Int,
        orderType: WordOrderType,
        excludeIds: Set<Long> = emptySet()
    ): List<Long>
    suspend fun updateBookStudyDay(bookId: Long, today: String)
    suspend fun recordAnswerResult(bookId: Long, isCorrect: Boolean, today: String)
}
