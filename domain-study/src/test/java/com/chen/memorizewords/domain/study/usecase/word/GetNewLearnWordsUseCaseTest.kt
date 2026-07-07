package com.chen.memorizewords.domain.study.usecase.word

import com.chen.memorizewords.core.common.paging.PageSlice
import com.chen.memorizewords.domain.word.model.WordListRow
import com.chen.memorizewords.domain.word.model.word.Word
import com.chen.memorizewords.domain.wordbook.model.WordBook
import com.chen.memorizewords.domain.wordbook.model.WordBookInfo
import com.chen.memorizewords.domain.wordbook.model.WordListQuery
import com.chen.memorizewords.domain.wordbook.repository.WordBookRepository
import com.chen.memorizewords.domain.wordbook.repository.WordOrderType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking

class GetNewLearnWordsUseCaseTest {

    @Test
    fun `returns lightweight unlearned word ids without loading full words`() = runBlocking {
        val repository = FakeWordBookRepository(selectedIds = listOf(3L, 1L))
        val useCase = GetNewLearnWordsUseCase(repository)

        val ids = useCase(
            bookId = 10L,
            count = 2,
            orderType = WordOrderType.ALPHABETIC_ASC,
            excludeIds = setOf(5L)
        )

        assertEquals(listOf(3L, 1L), ids)
        assertEquals(10L, repository.requestedBookId)
        assertEquals(2, repository.requestedCount)
        assertEquals(WordOrderType.ALPHABETIC_ASC, repository.requestedOrderType)
        assertEquals(setOf(5L), repository.requestedExcludeIds)
        assertFalse(repository.loadedFullWords)
    }

    private class FakeWordBookRepository(
        private val selectedIds: List<Long>
    ) : WordBookRepository {
        var requestedBookId: Long? = null
        var requestedCount: Int? = null
        var requestedOrderType: WordOrderType? = null
        var requestedExcludeIds: Set<Long>? = null
        var loadedFullWords = false

        override fun getMyWordBooksMinimalFlow(): Flow<List<WordBookInfo>> = emptyFlow()
        override fun getCurrentWordBookMinimalFlow(): Flow<WordBookInfo?> = emptyFlow()
        override suspend fun setCurrentWordBook(bookId: Long) = Unit
        override suspend fun deleteMyWordBook(bookId: Long): Result<Unit> = error("Not needed")
        override suspend fun createMyWordBook(
            title: String,
            category: String,
            description: String,
            words: List<String>
        ): Result<WordBookInfo> = error("Not needed")
        override suspend fun getCurrentWordBook(): WordBook? = null
        override suspend fun getBookNameById(bookId: Long): String? = null
        override suspend fun getWordListSummary(
            wordBookId: Long,
            now: Long
        ): com.chen.memorizewords.domain.wordbook.model.WordListSummary = com.chen.memorizewords.domain.wordbook.model.WordListSummary()
        override suspend fun getWordRowsPage(query: WordListQuery): PageSlice<WordListRow> = error("Not needed")
        override suspend fun getWordRowIds(query: WordListQuery, limit: Int): List<Long> = emptyList()
        override suspend fun getWordIdsPage(wordBookId: Long, pageIndex: Int, pageSize: Int): List<Long> = emptyList()
        override suspend fun getAllUnlearnedWordsForBook(bookId: Long): List<Word> {
            loadedFullWords = true
            return emptyList()
        }

        override suspend fun getUnlearnedWordIdsForBook(
            bookId: Long,
            count: Int,
            orderType: WordOrderType,
            excludeIds: Set<Long>
        ): List<Long> {
            requestedBookId = bookId
            requestedCount = count
            requestedOrderType = orderType
            requestedExcludeIds = excludeIds
            return selectedIds
        }

        override suspend fun updateBookStudyDay(bookId: Long, today: String) = Unit
        override suspend fun recordAnswerResult(bookId: Long, isCorrect: Boolean, today: String) = Unit
    }
}
