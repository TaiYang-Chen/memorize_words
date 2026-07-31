package com.chen.memorizewords.domain.floating.service

import com.chen.memorizewords.domain.floating.model.FloatingDevicePreferences
import com.chen.memorizewords.domain.floating.model.FloatingWordDisplayRecord
import com.chen.memorizewords.domain.floating.model.FloatingWordOrderType
import com.chen.memorizewords.domain.floating.model.FloatingWordSettings
import com.chen.memorizewords.domain.floating.model.FloatingWordSourceKey
import com.chen.memorizewords.domain.floating.model.FloatingWordSourceSnapshot
import com.chen.memorizewords.domain.floating.repository.FloatingWordDisplayRecordRepository
import com.chen.memorizewords.domain.floating.repository.FloatingDevicePreferencesRepository
import com.chen.memorizewords.domain.floating.repository.FloatingWordSourceRepository
import com.chen.memorizewords.domain.floating.repository.FloatingWordSettingsRepository
import com.chen.memorizewords.domain.word.model.word.Word
import com.chen.memorizewords.domain.word.model.word.WordDefinitions
import com.chen.memorizewords.domain.word.model.word.WordExample
import com.chen.memorizewords.domain.word.model.word.WordForm
import com.chen.memorizewords.domain.word.model.word.WordQuickLookupResult
import com.chen.memorizewords.domain.word.model.word.WordRoot
import com.chen.memorizewords.domain.word.repository.WordRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking

class FloatingReviewFacadeTest {

    @Test
    fun `facade exposes the latest source snapshot and loads only the selected word`() = runBlocking {
        val facade = FloatingReviewFacade(
            floatingWordSettingsRepository = FakeFloatingWordSettingsRepository(),
            floatingDevicePreferencesRepository = FakeFloatingDevicePreferencesRepository(),
            floatingWordDisplayRecordRepository = FakeFloatingWordDisplayRecordRepository(),
            floatingWordSourceRepository = FakeFloatingWordSourceRepository(listOf(1L, 3L)),
            wordRepository = FakeWordRepository(
                words = listOf(
                    testWord(1L, "able"),
                    testWord(2L, "mastered"),
                    testWord(3L, "brave"),
                    testWord(4L, "paused")
                )
            )
        )

        val snapshot = facade.loadWordSource(
            FloatingWordSettings(orderType = FloatingWordOrderType.MEMORY_CURVE)
        )

        assertEquals(listOf(1L, 3L), snapshot.wordIds)
        assertEquals("able", facade.loadWord(1L)?.word)
    }

    private class FakeFloatingWordSettingsRepository : FloatingWordSettingsRepository {
        override fun observeSettings(): Flow<FloatingWordSettings> = flowOf(FloatingWordSettings())
        override suspend fun getSettings(): FloatingWordSettings = FloatingWordSettings()
        override suspend fun saveSettings(settings: FloatingWordSettings) = Unit
    }

    private class FakeFloatingDevicePreferencesRepository : FloatingDevicePreferencesRepository {
        override fun observe(): Flow<FloatingDevicePreferences> = flowOf(FloatingDevicePreferences())
        override suspend fun get(): FloatingDevicePreferences = FloatingDevicePreferences()
        override suspend fun update(
            transform: (FloatingDevicePreferences) -> FloatingDevicePreferences
        ): FloatingDevicePreferences = transform(FloatingDevicePreferences())
        override suspend fun clear() = Unit
    }

    private class FakeFloatingWordDisplayRecordRepository : FloatingWordDisplayRecordRepository {
        override suspend fun recordDisplay(wordId: Long) = Unit
        override suspend fun getRecordByDate(date: String): FloatingWordDisplayRecord? = null
    }

    private class FakeFloatingWordSourceRepository(
        private val wordIds: List<Long>
    ) : FloatingWordSourceRepository {
        override suspend fun loadSnapshot(
            settings: FloatingWordSettings
        ): FloatingWordSourceSnapshot {
            return FloatingWordSourceSnapshot(
                sourceKey = FloatingWordSourceKey.CurrentBook(10L),
                orderType = settings.orderType,
                wordIds = wordIds
            )
        }
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
