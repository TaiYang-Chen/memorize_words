package com.chen.memorizewords.domain.floating.service
import com.chen.memorizewords.domain.floating.model.FloatingWordFieldType
import com.chen.memorizewords.domain.floating.model.FloatingWordSettings
import com.chen.memorizewords.domain.floating.model.FloatingWordSourceSnapshot
import com.chen.memorizewords.domain.word.model.word.Word
import com.chen.memorizewords.domain.word.model.word.WordDefinitions
import com.chen.memorizewords.domain.word.model.word.WordExample
import com.chen.memorizewords.domain.floating.repository.FloatingWordDisplayRecordRepository
import com.chen.memorizewords.domain.floating.repository.FloatingDevicePreferencesRepository
import com.chen.memorizewords.domain.floating.repository.FloatingWordSourceRepository
import com.chen.memorizewords.domain.floating.repository.FloatingWordSettingsRepository
import com.chen.memorizewords.domain.word.repository.WordRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class FloatingReviewFacade @Inject constructor(
    private val floatingWordSettingsRepository: FloatingWordSettingsRepository,
    private val floatingDevicePreferencesRepository: FloatingDevicePreferencesRepository,
    private val floatingWordDisplayRecordRepository: FloatingWordDisplayRecordRepository,
    private val floatingWordSourceRepository: FloatingWordSourceRepository,
    private val wordRepository: WordRepository
) {
    fun observeSettings(): Flow<FloatingWordSettings> = floatingWordSettingsRepository.observeSettings()

    suspend fun getSettings(): FloatingWordSettings = floatingWordSettingsRepository.getSettings()

    suspend fun saveSettings(settings: FloatingWordSettings) {
        floatingWordSettingsRepository.saveSettings(settings)
    }

    suspend fun updateSettings(
        transform: (FloatingWordSettings) -> FloatingWordSettings
    ): FloatingWordSettings = floatingWordSettingsRepository.updateSettings(transform)

    suspend fun updateBallPosition(
        x: Int,
        y: Int,
        dockState: com.chen.memorizewords.domain.floating.model.FloatingDockState?
    ) {
        floatingDevicePreferencesRepository.update { preferences ->
            preferences.copy(floatingBallX = x, floatingBallY = y, dockState = dockState)
        }
    }

    suspend fun recordDisplay(wordId: Long) {
        floatingWordDisplayRecordRepository.recordDisplay(wordId)
    }

    suspend fun loadWordSource(settings: FloatingWordSettings): FloatingWordSourceSnapshot =
        floatingWordSourceRepository.loadSnapshot(settings)

    suspend fun loadWord(wordId: Long): Word? {
        if (wordId <= 0L) return null
        return wordRepository.getWordById(wordId)
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

}

data class FloatingWordCardContent(
    val definitions: List<WordDefinitions>,
    val examples: List<WordExample>
)
