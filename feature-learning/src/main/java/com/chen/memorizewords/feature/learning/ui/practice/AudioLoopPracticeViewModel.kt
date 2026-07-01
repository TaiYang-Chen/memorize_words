package com.chen.memorizewords.feature.learning.ui.practice

import androidx.lifecycle.viewModelScope
import com.chen.memorizewords.core.ui.vm.BaseViewModel
import com.chen.memorizewords.domain.practice.PracticeEntryType
import com.chen.memorizewords.domain.practice.PracticeSettings
import com.chen.memorizewords.domain.practice.PracticeWordProvider
import com.chen.memorizewords.domain.practice.service.PracticeFacade
import com.chen.memorizewords.domain.word.query.WordReadFacade
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AudioLoopEntryUi(
    val id: Long,
    val word: String,
    val phoneticUs: String,
    val phoneticUk: String,
    val meaning: String,
    val exampleSentence: String,
    val exampleTranslation: String
)

data class AudioLoopUiState(
    val loading: Boolean = true,
    val entries: List<AudioLoopEntryUi> = emptyList(),
    val currentIndex: Int = 0,
    val persistedSettings: PracticeSettings = PracticeSettings(),
    val entryType: PracticeEntryType = PracticeEntryType.RANDOM
) {
    val currentEntry: AudioLoopEntryUi?
        get() = entries.getOrNull(currentIndex)
}

@HiltViewModel
class AudioLoopPracticeViewModel @Inject constructor(
    private val practiceFacade: PracticeFacade,
    private val wordProvider: PracticeWordProvider,
    private val wordReadFacade: WordReadFacade
) : BaseViewModel() {

    private val _uiState = MutableStateFlow(AudioLoopUiState())
    val uiState: StateFlow<AudioLoopUiState> = _uiState.asStateFlow()

    private var loadKey: String? = null

    init {
        observeSettings()
    }

    fun loadWithSelection(selectedIds: LongArray?, randomCount: Int) {
        val newLoadKey = buildPracticeSelectionKey(selectedIds, randomCount)
        if (loadKey == newLoadKey) return
        loadKey = newLoadKey

        val entryType = if (selectedIds == null || selectedIds.isEmpty()) {
            PracticeEntryType.RANDOM
        } else {
            PracticeEntryType.SELF
        }

        viewModelScope.launch {
            val currentSettings = normalizeSettings(practiceFacade.getSettings())
            _uiState.value = AudioLoopUiState(
                loading = true,
                persistedSettings = currentSettings,
                entryType = entryType
            )

            val words = withContext(Dispatchers.IO) {
                wordProvider.loadWords(
                    selectedIds = selectedIds,
                    randomCount = randomCount,
                    defaultLimit = 50
                )
            }
            val entries = withContext(Dispatchers.IO) {
                words.map { word -> mapEntry(word.id, word.word, word.phoneticUS, word.phoneticUK) }
            }
            _uiState.value = AudioLoopUiState(
                loading = false,
                entries = entries,
                currentIndex = 0,
                persistedSettings = currentSettings,
                entryType = entryType
            )
        }
    }

    fun onBackClick() {
        finish()
    }

    fun saveSettings(settings: PracticeSettings) {
        val normalized = normalizeSettings(settings)
        viewModelScope.launch {
            practiceFacade.saveSettings(normalized)
        }
    }

    private fun observeSettings() {
        viewModelScope.launch {
            practiceFacade.observeSettings().collect { settings ->
                val normalized = normalizeSettings(settings)
                _uiState.value = _uiState.value.copy(persistedSettings = normalized)
            }
        }
    }

    private suspend fun mapEntry(
        wordId: Long,
        rawWord: String,
        phoneticUs: String?,
        phoneticUk: String?
    ): AudioLoopEntryUi {
        val definitions = wordReadFacade.getWordDefinitions(wordId)
        val examples = wordReadFacade.getWordExamples(wordId)
        val definition = definitions.firstOrNull()
        val example = examples.firstOrNull { it.englishSentence.isNotBlank() } ?: examples.firstOrNull()
        return AudioLoopEntryUi(
            id = wordId,
            word = rawWord,
            phoneticUs = phoneticUs.orEmpty(),
            phoneticUk = phoneticUk.orEmpty(),
            meaning = definition?.let {
                "${it.partOfSpeech.abbr} ${it.meaningChinese}".trim()
            }.orEmpty(),
            exampleSentence = example?.englishSentence.orEmpty(),
            exampleTranslation = example?.chineseTranslation.orEmpty()
        )
    }

    private fun normalizeSettings(settings: PracticeSettings): PracticeSettings {
        return settings.copy(
            intervalSeconds = settings.intervalSeconds.coerceAtLeast(0),
            playTimes = settings.playTimes.coerceAtLeast(1),
            wordRepeatTimes = settings.wordRepeatTimes.coerceAtLeast(1),
            exampleRepeatTimes = settings.exampleRepeatTimes.coerceAtLeast(1),
            dictationPauseSeconds = settings.dictationPauseSeconds.coerceAtLeast(0),
            revealDelaySeconds = settings.revealDelaySeconds.coerceAtLeast(0),
            playbackSpeed = settings.playbackSpeed.coerceIn(0.5f, 2.0f),
            timedStopMinutes = settings.timedStopMinutes.coerceAtLeast(0)
        )
    }
}
