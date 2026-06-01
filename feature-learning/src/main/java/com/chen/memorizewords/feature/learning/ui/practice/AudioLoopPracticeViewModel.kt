package com.chen.memorizewords.feature.learning.ui.practice

import androidx.lifecycle.viewModelScope
import com.chen.memorizewords.core.common.resource.ResourceProvider
import com.chen.memorizewords.core.common.session.SessionTimer
import com.chen.memorizewords.core.ui.vm.BaseViewModel
import com.chen.memorizewords.domain.practice.AudioLoopPlaybackMode
import com.chen.memorizewords.domain.practice.PracticeEntryType
import com.chen.memorizewords.domain.practice.PracticeMode
import com.chen.memorizewords.domain.practice.PracticeSessionRecord
import com.chen.memorizewords.domain.practice.PracticeSettings
import com.chen.memorizewords.domain.practice.PracticeWordProvider
import com.chen.memorizewords.domain.word.query.WordReadFacade
import com.chen.memorizewords.domain.practice.service.PracticeFacade
import com.chen.memorizewords.feature.learning.R
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class AudioLoopPlayerState {
    EMPTY,
    WAITING,
    PLAYING,
    PAUSED
}

data class AudioLoopEntryUi(
    val id: Long,
    val word: String,
    val phonetic: String,
    val meaning: String,
    val exampleSentence: String,
    val exampleTranslation: String
)

data class AudioLoopPlaybackCue(
    val key: String,
    val text: String,
    val locale: String
)

data class AudioLoopPlaybackPlan(
    val entryId: Long,
    val settingsSnapshot: PracticeSettings,
    val cues: List<AudioLoopPlaybackCue>
)

sealed interface AudioLoopPlaybackCommand {
    data class Start(
        val plan: AudioLoopPlaybackPlan,
        val resumeIfPossible: Boolean
    ) : AudioLoopPlaybackCommand

    data object Pause : AudioLoopPlaybackCommand

    data object Stop : AudioLoopPlaybackCommand
}

data class AudioLoopUiState(
    val loading: Boolean = true,
    val entries: List<AudioLoopEntryUi> = emptyList(),
    val currentIndex: Int = 0,
    val playerState: AudioLoopPlayerState = AudioLoopPlayerState.EMPTY,
    val persistedSettings: PracticeSettings = PracticeSettings(),
    val playbackSettings: PracticeSettings = PracticeSettings(),
    val currentRepeat: Int = 0,
    val totalRepeats: Int = 0,
    val entryType: PracticeEntryType = PracticeEntryType.RANDOM
) {
    val currentEntry: AudioLoopEntryUi?
        get() = entries.getOrNull(currentIndex)
}

@HiltViewModel
class AudioLoopPracticeViewModel @Inject constructor(
    private val resourceProvider: ResourceProvider,
    private val practiceFacade: PracticeFacade,
    private val wordProvider: PracticeWordProvider,
    private val wordReadFacade: WordReadFacade
) : BaseViewModel() {

    private val _uiState = MutableStateFlow(AudioLoopUiState())
    val uiState: StateFlow<AudioLoopUiState> = _uiState.asStateFlow()

    private val _commands = MutableSharedFlow<AudioLoopPlaybackCommand>(extraBufferCapacity = 8)
    val commands: SharedFlow<AudioLoopPlaybackCommand> = _commands.asSharedFlow()

    private var loadKey: String? = null
    private val sessionTimer = SessionTimer()
    private var sessionSaved: Boolean = false
    private var sessionCreatedAt: Long = 0L
    private var sessionWordIds: List<Long> = emptyList()
    private var hasStartedPlayback: Boolean = false
    private var hasCompletedAtLeastOneEntry: Boolean = false
    private val completedWordIds = linkedSetOf<Long>()
    private val roundProcessedEntryIds = linkedSetOf<Long>()
    private val roundSuccessfulEntryIds = linkedSetOf<Long>()

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
                playbackSettings = currentSettings,
                totalRepeats = currentSettings.playTimes,
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
            resetSession(entries, entryType)
            _uiState.value = AudioLoopUiState(
                loading = false,
                entries = entries,
                currentIndex = 0,
                playerState = if (entries.isEmpty()) {
                    AudioLoopPlayerState.EMPTY
                } else {
                    AudioLoopPlayerState.WAITING
                },
                persistedSettings = currentSettings,
                playbackSettings = currentSettings,
                currentRepeat = 0,
                totalRepeats = currentSettings.playTimes,
                entryType = entryType
            )
            _commands.tryEmit(AudioLoopPlaybackCommand.Stop)
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

    fun onPlayPauseClick() {
        val state = _uiState.value
        val entry = state.currentEntry ?: return
        when (state.playerState) {
            AudioLoopPlayerState.EMPTY -> Unit

            AudioLoopPlayerState.PLAYING -> {
                _uiState.value = state.copy(playerState = AudioLoopPlayerState.PAUSED)
                _commands.tryEmit(AudioLoopPlaybackCommand.Pause)
            }

            AudioLoopPlayerState.PAUSED -> {
                val plan = buildPlan(entry, state.playbackSettings)
                _uiState.value = state.copy(playerState = AudioLoopPlayerState.PLAYING)
                _commands.tryEmit(
                    AudioLoopPlaybackCommand.Start(
                        plan = plan,
                        resumeIfPossible = true
                    )
                )
            }

            AudioLoopPlayerState.WAITING -> {
                val snapshot = normalizeSettings(state.persistedSettings)
                val plan = buildPlan(entry, snapshot)
                resetPlaybackRound()
                _uiState.value = state.copy(
                    playerState = AudioLoopPlayerState.PLAYING,
                    playbackSettings = snapshot,
                    currentRepeat = 1,
                    totalRepeats = snapshot.playTimes
                )
                _commands.tryEmit(
                    AudioLoopPlaybackCommand.Start(
                        plan = plan,
                        resumeIfPossible = false
                    )
                )
            }
        }
    }

    fun onPreviousClick() {
        val state = _uiState.value
        if (state.currentIndex <= 0) return
        switchToIndex(state.currentIndex - 1)
    }

    fun onNextClick() {
        val state = _uiState.value
        if (state.currentIndex >= state.entries.lastIndex) return
        switchToIndex(state.currentIndex + 1)
    }

    fun onPlaylistItemSelected(index: Int) {
        val state = _uiState.value
        if (index !in state.entries.indices || index == state.currentIndex) return
        switchToIndex(index)
    }

    fun onPlaybackRepeatStarted(entryId: Long, repeatNumber: Int, settingsSnapshot: PracticeSettings) {
        val state = _uiState.value
        val entry = state.currentEntry ?: return
        if (entry.id != entryId) return
        hasStartedPlayback = true
        _uiState.value = state.copy(
            playerState = AudioLoopPlayerState.PLAYING,
            playbackSettings = settingsSnapshot,
            currentRepeat = repeatNumber.coerceAtLeast(1),
            totalRepeats = settingsSnapshot.playTimes.coerceAtLeast(1)
        )
    }

    fun onPlaybackPaused() {
        val state = _uiState.value
        if (state.playerState == AudioLoopPlayerState.EMPTY) return
        _uiState.value = state.copy(playerState = AudioLoopPlayerState.PAUSED)
    }

    fun onPlaybackInterrupted() {
        onPlaybackPaused()
    }

    fun onPlaybackCompleted(entryId: Long) {
        handleEntryFinished(entryId = entryId, wasSuccessful = true)
    }

    fun onPlaybackEntryFailed(entryId: Long) {
        handleEntryFinished(entryId = entryId, wasSuccessful = false)
    }

    fun onPageVisible() {
        if (_uiState.value.entries.isEmpty()) return
        sessionTimer.start()
    }

    fun onPageHidden() {
        val durationMs = sessionTimer.pause()
        if (durationMs <= 0L) return
        viewModelScope.launch(Dispatchers.IO) {
            practiceFacade.addPracticeDuration(durationMs)
        }
    }

    fun finishSession() {
        if (sessionSaved) return
        onPageHidden()
        val durationMs = sessionTimer.finish()
        if (!shouldPersistSession(durationMs)) return
        sessionSaved = true
        val state = _uiState.value
        val record = PracticeSessionRecord(
            id = 0L,
            date = "",
            mode = PracticeMode.AUDIO_LOOP,
            entryType = state.entryType,
            entryCount = sessionWordIds.size,
            durationMs = durationMs,
            createdAt = sessionCreatedAt,
            wordIds = sessionWordIds,
            questionCount = sessionWordIds.size,
            completedCount = completedWordIds.size,
            correctCount = 0,
            submitCount = completedWordIds.size
        )
        viewModelScope.launch(Dispatchers.IO) {
            practiceFacade.saveSessionRecord(record)
        }
    }

    private fun handleEntryFinished(entryId: Long, wasSuccessful: Boolean) {
        val state = _uiState.value
        val entry = state.currentEntry ?: return
        if (entry.id != entryId) return

        if (wasSuccessful) {
            completedWordIds += entryId
            hasCompletedAtLeastOneEntry = true
        }
        val shouldStopForFailedRound = recordEntryOutcome(
            entryId = entryId,
            wasSuccessful = wasSuccessful,
            totalEntryCount = state.entries.size
        )
        val nextSettings = normalizeSettings(state.persistedSettings)
        if (shouldStopForFailedRound) {
            stopPlaybackAfterFailedRound(state, nextSettings)
            return
        }
        val hasNext = state.currentIndex < state.entries.lastIndex
        if (hasNext) {
            val nextIndex = state.currentIndex + 1
            val nextEntry = state.entries[nextIndex]
            _uiState.value = state.copy(
                currentIndex = nextIndex,
                playerState = AudioLoopPlayerState.PLAYING,
                playbackSettings = nextSettings,
                currentRepeat = 1,
                totalRepeats = nextSettings.playTimes
            )
            _commands.tryEmit(
                AudioLoopPlaybackCommand.Start(
                    plan = buildPlan(nextEntry, nextSettings),
                    resumeIfPossible = false
                )
            )
            return
        }

        if (nextSettings.loopEnabled && state.entries.isNotEmpty()) {
            val firstEntry = state.entries.first()
            _uiState.value = state.copy(
                currentIndex = 0,
                playerState = AudioLoopPlayerState.PLAYING,
                playbackSettings = nextSettings,
                currentRepeat = 1,
                totalRepeats = nextSettings.playTimes
            )
            _commands.tryEmit(
                AudioLoopPlaybackCommand.Start(
                    plan = buildPlan(firstEntry, nextSettings),
                    resumeIfPossible = false
                )
            )
            return
        }

        _uiState.value = state.copy(
            playerState = AudioLoopPlayerState.WAITING,
            playbackSettings = nextSettings,
            currentRepeat = 0,
            totalRepeats = nextSettings.playTimes
        )
    }

    private fun switchToIndex(index: Int) {
        val state = _uiState.value
        val targetEntry = state.entries.getOrNull(index) ?: return
        val nextSettings = normalizeSettings(state.persistedSettings)
        val shouldAutoPlay = state.playerState == AudioLoopPlayerState.PLAYING
        resetPlaybackRound()
        _uiState.value = state.copy(
            currentIndex = index,
            playerState = if (shouldAutoPlay) {
                AudioLoopPlayerState.PLAYING
            } else {
                AudioLoopPlayerState.WAITING
            },
            playbackSettings = nextSettings,
            currentRepeat = if (shouldAutoPlay) 1 else 0,
            totalRepeats = nextSettings.playTimes
        )
        if (shouldAutoPlay) {
            _commands.tryEmit(
                AudioLoopPlaybackCommand.Start(
                    plan = buildPlan(targetEntry, nextSettings),
                    resumeIfPossible = false
                )
            )
        } else {
            _commands.tryEmit(AudioLoopPlaybackCommand.Stop)
        }
    }

    private fun observeSettings() {
        viewModelScope.launch {
            practiceFacade.observeSettings().collect { settings ->
                val normalized = normalizeSettings(settings)
                val current = _uiState.value
                _uiState.value = current.copy(
                    persistedSettings = normalized,
                    playbackSettings = when (current.playerState) {
                        AudioLoopPlayerState.PLAYING,
                        AudioLoopPlayerState.PAUSED -> current.playbackSettings

                        else -> normalized
                    },
                    totalRepeats = when (current.playerState) {
                        AudioLoopPlayerState.PLAYING,
                        AudioLoopPlayerState.PAUSED -> current.totalRepeats

                        else -> normalized.playTimes
                    }
                )
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
            phonetic = phoneticUs?.takeIf { it.isNotBlank() }
                ?: phoneticUk?.takeIf { it.isNotBlank() }
                ?: "",
            meaning = definition?.let {
                "${it.partOfSpeech.abbr} ${it.meaningChinese}".trim()
            }.orEmpty(),
            exampleSentence = example?.englishSentence.orEmpty(),
            exampleTranslation = example?.chineseTranslation.orEmpty()
        )
    }

    private fun buildPlan(entry: AudioLoopEntryUi, settings: PracticeSettings): AudioLoopPlaybackPlan {
        val normalized = normalizeSettings(settings)
        val cues = buildList {
            add(
                AudioLoopPlaybackCue(
                    key = "word_${entry.id}_${entry.word.lowercase(Locale.US)}",
                    text = entry.word,
                    locale = "en-US"
                )
            )
            if (
                normalized.playbackMode == AudioLoopPlaybackMode.WORD_WITH_EXAMPLE &&
                entry.exampleSentence.isNotBlank()
            ) {
                add(
                    AudioLoopPlaybackCue(
                        key = "example_${entry.id}_${entry.exampleSentence.hashCode()}",
                        text = entry.exampleSentence,
                        locale = "en-US"
                    )
                )
            }
        }
        return AudioLoopPlaybackPlan(
            entryId = entry.id,
            settingsSnapshot = normalized,
            cues = cues
        )
    }

    private fun normalizeSettings(settings: PracticeSettings): PracticeSettings {
        return settings.copy(
            intervalSeconds = settings.intervalSeconds.coerceAtLeast(0),
            playTimes = settings.playTimes.coerceAtLeast(1)
        )
    }

    private fun resetSession(entries: List<AudioLoopEntryUi>, entryType: PracticeEntryType) {
        sessionTimer.reset()
        sessionSaved = false
        sessionCreatedAt = System.currentTimeMillis()
        sessionWordIds = entries.map { it.id }
        hasStartedPlayback = false
        hasCompletedAtLeastOneEntry = false
        completedWordIds.clear()
        resetPlaybackRound()
        if (entries.isEmpty()) {
            sessionCreatedAt = 0L
        }
        _uiState.value = _uiState.value.copy(entryType = entryType)
    }

    private fun shouldPersistSession(durationMs: Long): Boolean {
        return durationMs > 0L &&
            sessionWordIds.isNotEmpty() &&
            hasStartedPlayback &&
            hasCompletedAtLeastOneEntry
    }

    private fun recordEntryOutcome(
        entryId: Long,
        wasSuccessful: Boolean,
        totalEntryCount: Int
    ): Boolean {
        roundProcessedEntryIds += entryId
        if (wasSuccessful) {
            roundSuccessfulEntryIds += entryId
        }
        val isFullRoundCompleted =
            totalEntryCount > 0 && roundProcessedEntryIds.size >= totalEntryCount
        if (!isFullRoundCompleted) return false
        val allEntriesFailed = roundSuccessfulEntryIds.isEmpty()
        resetPlaybackRound()
        return allEntriesFailed
    }

    private fun stopPlaybackAfterFailedRound(
        state: AudioLoopUiState,
        nextSettings: PracticeSettings
    ) {
        _uiState.value = state.copy(
            playerState = AudioLoopPlayerState.WAITING,
            playbackSettings = nextSettings,
            currentRepeat = 0,
            totalRepeats = nextSettings.playTimes
        )
        _commands.tryEmit(AudioLoopPlaybackCommand.Stop)
        showToast(resourceProvider.getString(R.string.feature_learning_audio_loop_round_failed))
    }

    private fun resetPlaybackRound() {
        roundProcessedEntryIds.clear()
        roundSuccessfulEntryIds.clear()
    }
}
