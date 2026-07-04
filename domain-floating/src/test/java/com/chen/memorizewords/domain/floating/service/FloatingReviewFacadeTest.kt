package com.chen.memorizewords.domain.floating.service

import com.chen.memorizewords.core.common.paging.PageSlice
import com.chen.memorizewords.domain.floating.model.FloatingDockState
import com.chen.memorizewords.domain.floating.model.FloatingWordDisplayRecord
import com.chen.memorizewords.domain.floating.model.FloatingWordOrderType
import com.chen.memorizewords.domain.floating.model.FloatingWordSettings
import com.chen.memorizewords.domain.floating.repository.FloatingWordDisplayRecordRepository
import com.chen.memorizewords.domain.floating.repository.FloatingWordSettingsRepository
import com.chen.memorizewords.domain.study.model.progress.word.WordLearningState
import com.chen.memorizewords.domain.study.repository.WordLearningRepository
import com.chen.memorizewords.domain.word.model.WordListRow
import com.chen.memorizewords.domain.word.model.word.Word
import com.chen.memorizewords.domain.word.model.word.WordDefinitions
import com.chen.memorizewords.domain.word.model.word.WordExample
import com.chen.memorizewords.domain.word.model.word.WordForm
import com.chen.memorizewords.domain.word.model.word.WordQuickLookupResult
import com.chen.memorizewords.domain.word.model.word.WordRoot
import com.chen.memorizewords.domain.word.repository.WordRepository
import com.chen.memorizewords.domain.wordbook.model.WordBook
import com.chen.memorizewords.domain.wordbook.model.WordBookInfo
import com.chen.memorizewords.domain.wordbook.model.WordListQuery
import com.chen.memorizewords.domain.wordbook.repository.WordBookRepository
import com.chen.memorizewords.domain.wordbook.repository.WordOrderType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking

class FloatingReviewFacadeTest {

    @Test
    fun `current book floating words use only reviewable learning ids`() = runBlocking {
        val facade = FloatingReviewFacade(
            floatingWordSettingsRepository = FakeFloatingWordSettingsRepository(),
            floatingWordDisplayRecordRepository = FakeFloatingWordDisplayRecordRepository(),
            wordLearningRepository = FakeWordLearningRepository(reviewableIds = listOf(1L, 3L)),
            wordRepository = FakeWordRepository(
                words = listOf(
                    testWord(1L, "able"),
                    testWord(2L, "mastered"),
                    testWord(3L, "brave"),
                    testWord(4L, "paused")
                )
            ),
            wordBookRepository = FakeWordBookRepository()
        )

        val words = facade.loadWords(
            FloatingWordSettings(orderType = FloatingWordOrderType.MEMORY_CURVE)
        )

        assertEquals(listOf(1L, 3L), words.map { it.id })
    }

    private class FakeFloatingWordSettingsRepository : FloatingWordSettingsRepository {
        override fun observeSettings(): Flow<FloatingWordSettings> = flowOf(FloatingWordSettings())
        override suspend fun getSettings(): FloatingWordSettings = FloatingWordSettings()
        override suspend fun saveSettings(settings: FloatingWordSettings) = Unit
        override suspend fun updateBallPosition(x: Int, y: Int, dockState: FloatingDockState?) = Unit
    }

    private class FakeFloatingWordDisplayRecordRepository : FloatingWordDisplayRecordRepository {
        override suspend fun recordDisplay(wordId: Long) = Unit
        override suspend fun getRecordByDate(date: String): FloatingWordDisplayRecord? = null
    }

    private class FakeWordLearningRepository(
        private val reviewableIds: List<Long>
    ) : WordLearningRepository {
        override suspend fun getLearningStatesByIds(
            wordBookId: Long,
            ids: List<Long>
        ): Map<Long, WordLearningState> {
            return ids.associateWith { id ->
                WordLearningState(
                    wordId = id,
                    bookId = wordBookId,
                    nextReviewTime = id
                )
            }
        }

        override suspend fun getLearningStatesByBookId(bookId: Long): List<WordLearningState> = emptyList()

        override suspend fun getLearnedWordIdsByBook(bookId: Long): List<Long> = reviewableIds

        override suspend fun deleteLearningWordByBookId(bookId: Long) = Unit
    }

    private class FakeWordRepository(
        words: List<Word>
    ) : WordRepository {
        private val wordsById = words.associateBy { it.id }

        override suspend fun getWordsByIds(ids: List<Long>): List<Word> = ids.mapNotNull(wordsById::get)
        override suspend fun getWordById(wordId: Long): Word? = wordsById[wordId]
        override suspend fun getWordForms(wordId: Long): List<WordForm> = emptyList()
        override suspend fun getRootWordByWordId(wordId: Long): List<WordRoot> = emptyList()
        override suspend fun getWordExamples(wordId: Long): List<WordExample> = emptyList()
        override suspend fun getWordDefinitions(wordId: Long): List<WordDefinitions> = emptyList()
        override suspend fun getRandomDefinition(wordId: Long): WordDefinitions = error("Not needed")
        override suspend fun getRandomDefinitionsByPos(wordId: Long, limit: Int): List<WordDefinitions> = emptyList()
        override suspend fun updateWordStatus(bookId: Long, word: Word, quality: Int): Boolean = error("Not needed")
        override suspend fun setWordAsMastered(bookId: Long, word: Word) = error("Not needed")
        override suspend fun getWordByWordString(word: String): Word? = null
        override suspend fun lookupWordQuick(normalizedWord: String, rawWord: String): WordQuickLookupResult {
            error("Not needed")
        }
    }

    private class FakeWordBookRepository : WordBookRepository {
        override fun getMyWordBooksMinimalFlow(): Flow<List<WordBookInfo>> = emptyFlow()
        override fun getCurrentWordBookMinimalFlow(): Flow<WordBookInfo?> = emptyFlow()
        override suspend fun setCurrentWordBook(bookId: Long) = Unit
        override suspend fun deleteMyWordBook(bookId: Long): Result<Unit> = error("Not needed")
        override suspend fun getCurrentWordBook(): WordBook = WordBook(
            id = 10L,
            title = "Book",
            category = "test",
            imgUrl = "",
            description = "",
            totalWords = 4,
            isSelected = true,
            isPublic = false,
            createdByUserId = null
        )

        override suspend fun getBookNameById(bookId: Long): String? = null
        override suspend fun getWordListSummary(
            wordBookId: Long,
            now: Long
        ): com.chen.memorizewords.domain.wordbook.model.WordListSummary = com.chen.memorizewords.domain.wordbook.model.WordListSummary()
        override suspend fun getWordRowsPage(query: WordListQuery): PageSlice<WordListRow> = error("Not needed")
        override suspend fun getWordRowIds(query: WordListQuery, limit: Int): List<Long> = emptyList()
        override suspend fun getWordIdsPage(wordBookId: Long, pageIndex: Int, pageSize: Int): List<Long> = emptyList()
        override suspend fun getAllUnlearnedWordsForBook(bookId: Long): List<Word> = emptyList()
        override suspend fun getUnlearnedWordIdsForBook(
            bookId: Long,
            count: Int,
            orderType: WordOrderType,
            excludeIds: Set<Long>
        ): List<Long> = emptyList()
        override suspend fun updateBookStudyDay(bookId: Long, today: String) = Unit
        override suspend fun recordAnswerResult(bookId: Long, isCorrect: Boolean, today: String) = Unit
    }
}

private fun testWord(id: Long, value: String): Word {
    return Word(
        id = id,
        word = value,
        normalizedWord = value,
        phoneticUS = null,
        phoneticUK = null,
        hasIrregularForms = false,
        memoryTip = null,
        mnemonicImageUrl = null,
        memoryAssociations = emptyList(),
        wordFamily = null,
        synonyms = emptyList(),
        antonyms = emptyList(),
        tags = emptyList(),
        notes = null,
        rootMemoryTip = null
    )
}
