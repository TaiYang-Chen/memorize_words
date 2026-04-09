package com.chen.memorizewords.domain.service.floating

import com.chen.memorizewords.domain.model.floating.FloatingDockState
import com.chen.memorizewords.domain.model.floating.FloatingWordFieldType
import com.chen.memorizewords.domain.model.floating.FloatingWordOrderType
import com.chen.memorizewords.domain.model.floating.FloatingWordSettings
import com.chen.memorizewords.domain.model.floating.FloatingWordSourceType
import com.chen.memorizewords.domain.model.words.word.Word
import com.chen.memorizewords.domain.model.words.word.WordDefinitions
import com.chen.memorizewords.domain.model.words.word.WordExample
import com.chen.memorizewords.domain.repository.WordBookRepository
import com.chen.memorizewords.domain.repository.WordLearningRepository
import com.chen.memorizewords.domain.repository.floating.FloatingWordDisplayRecordRepository
import com.chen.memorizewords.domain.repository.floating.FloatingWordSettingsRepository
import com.chen.memorizewords.domain.repository.word.WordRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class FloatingReviewFacade @Inject constructor(
    private val floatingWordSettingsRepository: FloatingWordSettingsRepository,
    private val floatingWordDisplayRecordRepository: FloatingWordDisplayRecordRepository,
    private val wordLearningRepository: WordLearningRepository,
    private val wordRepository: WordRepository,
    private val wordBookRepository: WordBookRepository
) {
    fun observeSettings(): Flow<FloatingWordSettings> = floatingWordSettingsRepository.observeSettings()

    suspend fun getSettings(): FloatingWordSettings = floatingWordSettingsRepository.getSettings()

    suspend fun saveSettings(settings: FloatingWordSettings) {
        floatingWordSettingsRepository.saveSettings(settings)
    }

    suspend fun updateBallPosition(x: Int, y: Int, dockState: FloatingDockState?) {
        floatingWordSettingsRepository.updateBallPosition(x, y, dockState)
    }

    suspend fun recordDisplay(wordId: Long) {
        floatingWordDisplayRecordRepository.recordDisplay(wordId)
    }

    suspend fun loadWords(settings: FloatingWordSettings): List<Word> {
        return when (settings.sourceType) {
            FloatingWordSourceType.CURRENT_BOOK -> {
                val currentBook = wordBookRepository.getCurrentWordBook() ?: return emptyList()
                val learnedIds = wordLearningRepository.getLearnedWordIdsByBook(currentBook.id)
                buildOrderedWords(
                    wordIds = learnedIds,
                    orderType = settings.orderType,
                    bookId = if (settings.orderType == FloatingWordOrderType.MEMORY_CURVE) {
                        currentBook.id
                    } else {
                        null
                    }
                )
            }

            FloatingWordSourceType.SELF_SELECT -> {
                buildOrderedWords(
                    wordIds = settings.selectedWordIds,
                    orderType = settings.orderType,
                    bookId = wordBookRepository.getCurrentWordBook()?.id
                )
            }
        }
    }

    suspend fun loadCardContent(
        word: Word,
        settings: FloatingWordSettings
    ): FloatingWordCardContent {
        val enabledTypes = settings.fieldConfigs.filter { it.enabled }.map { it.type }
        val definitions = if (
            enabledTypes.contains(FloatingWordFieldType.MEANING) ||
            enabledTypes.contains(FloatingWordFieldType.PART_OF_SPEECH)
        ) {
            wordRepository.getWordDefinitions(word.id)
        } else {
            emptyList()
        }
        val examples = if (enabledTypes.contains(FloatingWordFieldType.EXAMPLE)) {
            wordRepository.getWordExamples(word.id)
        } else {
            emptyList()
        }
        return FloatingWordCardContent(definitions = definitions, examples = examples)
    }

    private suspend fun buildOrderedWords(
        wordIds: List<Long>,
        orderType: FloatingWordOrderType,
        bookId: Long?
    ): List<Word> {
        if (wordIds.isEmpty()) return emptyList()

        val orderedIds = if (orderType == FloatingWordOrderType.MEMORY_CURVE && bookId != null) {
            val states = wordLearningRepository.getLearningStatesByIds(bookId, wordIds)
            wordIds.sortedBy { states[it]?.nextReviewTime ?: Long.MAX_VALUE }
        } else {
            wordIds
        }

        val words = wordRepository.getWordsByIds(orderedIds)
        val wordMap = words.associateBy { it.id }
        val orderedWords = orderedIds.mapNotNull { wordMap[it] }

        return when (orderType) {
            FloatingWordOrderType.RANDOM -> orderedWords
            FloatingWordOrderType.MEMORY_CURVE -> orderedWords
            FloatingWordOrderType.ALPHABETIC_ASC -> orderedWords.sortedBy { it.normalizedWord }
            FloatingWordOrderType.ALPHABETIC_DESC -> orderedWords.sortedByDescending { it.normalizedWord }
            FloatingWordOrderType.LENGTH_ASC -> orderedWords.sortedBy { it.word.length }
            FloatingWordOrderType.LENGTH_DESC -> orderedWords.sortedByDescending { it.word.length }
        }
    }
}

data class FloatingWordCardContent(
    val definitions: List<WordDefinitions>,
    val examples: List<WordExample>
)
