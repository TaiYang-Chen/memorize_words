package com.chen.memorizewords.domain.study.usecase.word

import com.chen.memorizewords.domain.study.model.progress.word.WordLearningState
import com.chen.memorizewords.domain.study.repository.WordLearningRepository
import com.chen.memorizewords.domain.word.model.word.Word
import com.chen.memorizewords.domain.word.model.word.WordDefinitions
import com.chen.memorizewords.domain.word.model.word.WordExample
import com.chen.memorizewords.domain.word.model.word.WordForm
import com.chen.memorizewords.domain.word.model.word.WordQuickLookupResult
import com.chen.memorizewords.domain.word.model.word.WordRoot
import com.chen.memorizewords.domain.word.repository.WordRepository
import com.chen.memorizewords.domain.wordbook.repository.WordOrderType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking

class GetReviewWordsUseCaseTest {

    @Test
    fun `returns only reviewable word ids exposed by learning repository`() = runBlocking {
        val useCase = GetReviewWordsUseCase(
            wordLearningRepository = FakeWordLearningRepository(
                reviewableIds = listOf(1L, 3L)
            ),
            wordRepository = FakeWordRepository(
                words = listOf(
                    testWord(1L, "able"),
                    testWord(2L, "mastered"),
                    testWord(3L, "brave"),
                    testWord(4L, "paused")
                )
            )
        )

        val words = useCase(
            bookId = 10L,
            count = 10,
            orderType = WordOrderType.ALPHABETIC_ASC
        )

        assertEquals(listOf(1L, 3L), words.map { it.id })
    }

    @Test
    fun `excludes words reviewed earlier today before selecting review words`() = runBlocking {
        val useCase = GetReviewWordsUseCase(
            wordLearningRepository = FakeWordLearningRepository(
                reviewableIds = listOf(1L, 2L, 3L)
            ),
            wordRepository = FakeWordRepository(
                words = listOf(
                    testWord(1L, "able"),
                    testWord(2L, "brave"),
                    testWord(3L, "calm")
                )
            )
        )

        val words = useCase(
            bookId = 10L,
            count = 10,
            orderType = WordOrderType.ALPHABETIC_ASC,
            excludeIds = setOf(2L)
        )

        assertEquals(listOf(1L, 3L), words.map { it.id })
    }

    private class FakeWordLearningRepository(
        private val reviewableIds: List<Long>
    ) : WordLearningRepository {
        override suspend fun getLearningStatesByIds(
            wordBookId: Long,
            ids: List<Long>
        ): Map<Long, WordLearningState> = emptyMap()

        override suspend fun getLearningStatesByBookId(bookId: Long): List<WordLearningState> = emptyList()

        override suspend fun getLearnedWordIdsByBook(bookId: Long): List<Long> = reviewableIds

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
        override suspend fun getWordByWordString(word: String): Word? = null
        override suspend fun lookupWordQuick(normalizedWord: String, rawWord: String): WordQuickLookupResult {
            error("Not needed")
        }
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
