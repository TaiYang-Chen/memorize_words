package com.chen.memorizewords.feature.learning.ui.practice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chen.memorizewords.core.common.resource.ResourceProvider
import com.chen.memorizewords.domain.model.words.word.Word
import com.chen.memorizewords.domain.practice.PracticeWordProvider
import com.chen.memorizewords.domain.usecase.practice.SynthesizeSpeechUseCase
import com.chen.memorizewords.domain.usecase.word.GetWordDefinitionsUseCase
import com.chen.memorizewords.feature.learning.R
import com.chen.memorizewords.speech.api.SpeechAudioSuccess
import com.chen.memorizewords.speech.api.SpeechTask
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class SpellingResultState {
    UNANSWERED,
    RETRYABLE_WRONG,
    CORRECT,
    REVEALED_WRONG,
    COMPLETED
}

data class PracticeSessionSummary(
    val questionCount: Int = 0,
    val completedCount: Int = 0,
    val correctCount: Int = 0,
    val submitCount: Int = 0
)

@HiltViewModel
class SpellingPracticeViewModel @Inject constructor(
    private val getWordDefinitions: GetWordDefinitionsUseCase,
    private val synthesizeSpeech: SynthesizeSpeechUseCase,
    private val wordProvider: PracticeWordProvider,
    private val resourceProvider: ResourceProvider
) : ViewModel() {

    private val sessionEngine = SpellingSessionEngine(resourceProvider)
    private val assetLoader by lazy(LazyThreadSafetyMode.NONE) {
        SpellingAssetLoader(
            scope = viewModelScope,
            getWordDefinitions = getWordDefinitions,
            synthesizeSpeech = synthesizeSpeech,
            resourceProvider = resourceProvider
        )
    }

    data class LetterItem(
        val id: Int,
        val letter: Char,
        val enabled: Boolean
    )

    data class AnswerSlot(
        val letter: String = "",
        val isHintLocked: Boolean = false
    )

    data class SpellingUiState(
        val meaning: String = "",
        val wordLengthHint: String = "",
        val wordLength: Int = 0,
        val currentAnswer: String = "",
        val answerSlots: List<AnswerSlot> = emptyList(),
        val letters: List<LetterItem> = emptyList(),
        val feedback: String = "",
        val progressText: String = "",
        val progressValue: Int = 0,
        val progressMax: Int = 0,
        val currentWord: String = "",
        val speech: SpeechAudioSuccess? = null,
        val resultState: SpellingResultState = SpellingResultState.UNANSWERED,
        val attemptCount: Int = 0,
        val hintCount: Int = 0,
        val canSubmit: Boolean = false,
        val canNext: Boolean = false,
        val canHint: Boolean = false,
        val canEditAnswer: Boolean = true,
        val isCompleted: Boolean = false,
        val autoPlayRequestId: Int = 0,
        val summary: PracticeSessionSummary = PracticeSessionSummary(),
        val summaryText: String = ""
    )

    private val _uiState = MutableStateFlow(SpellingUiState())
    val uiState: StateFlow<SpellingUiState> = _uiState.asStateFlow()

    private val _sessionWordIds = MutableStateFlow<List<Long>>(emptyList())
    val sessionWordIds: StateFlow<List<Long>> = _sessionWordIds.asStateFlow()

    private var loadKey: String? = null
    private var words: List<Word> = emptyList()
    private var index: Int = 0
    private var answerWord: String = ""
    private var renderToken: Int = 0
    private var currentAnswer: String = ""
    private var hintLockedLength: Int = 0
    private var attemptCount: Int = 0
    private var hintCount: Int = 0
    private var currentResultState: SpellingResultState = SpellingResultState.UNANSWERED
    private var completedCount: Int = 0
    private var correctCount: Int = 0
    private var submitCount: Int = 0
    private var autoPlayRequestId: Int = 0
    private var letterPoolChars: List<Char> = emptyList()

    fun loadWithSelection(selectedIds: LongArray?, randomCount: Int) {
        val newLoadKey = buildPracticeSelectionKey(selectedIds, randomCount)
        if (loadKey == newLoadKey) return
        loadKey = newLoadKey
        loadWords(selectedIds, randomCount)
    }

    fun onLetterClick(id: Int) {
        val item = _uiState.value.letters.firstOrNull { it.id == id } ?: return
        if (!item.enabled) return
        appendAnswer(item.letter)
    }

    fun onKeyboardInputChanged(input: String) {
        if (_uiState.value.isCompleted || !_uiState.value.canEditAnswer) return
        val sanitized = sessionEngine.reconcileKeyboardInput(
            answerWord = answerWord,
            hintLockedLength = hintLockedLength,
            input = input
        )
        if (sanitized == currentAnswer) return
        currentAnswer = sanitized
        if (currentResultState == SpellingResultState.RETRYABLE_WRONG) {
            currentResultState = SpellingResultState.UNANSWERED
        }
        publishCurrentState(feedback = "")
    }

    fun onDelete() {
        if (_uiState.value.isCompleted || !_uiState.value.canEditAnswer) return
        if (currentAnswer.length <= hintLockedLength) return
        currentAnswer = currentAnswer.dropLast(1)
        if (currentResultState == SpellingResultState.RETRYABLE_WRONG) {
            currentResultState = SpellingResultState.UNANSWERED
        }
        publishCurrentState(feedback = "")
    }

    fun onHint() {
        if (!_uiState.value.canHint || answerWord.isBlank()) return
        val hintResult = sessionEngine.applyHint(answerWord, currentAnswer, hintLockedLength) ?: return
        currentAnswer = hintResult.answer
        hintLockedLength = hintResult.hintLockedLength
        hintCount = 1
        if (currentResultState == SpellingResultState.RETRYABLE_WRONG) {
            currentResultState = SpellingResultState.UNANSWERED
        }
        publishCurrentState(
            feedback = resourceProvider.getString(
                R.string.practice_spelling_hint_applied,
                hintLockedLength,
                answerWord.length
            )
        )
    }

    fun onSubmit() {
        val state = _uiState.value
        if (!state.canSubmit || answerWord.isBlank()) return
        submitCount += 1
        attemptCount += 1
        val input = currentAnswer.trim()
        val isCorrect = input.equals(answerWord, ignoreCase = true)
        if (isCorrect) {
            currentResultState = SpellingResultState.CORRECT
            completedCount += 1
            correctCount += 1
            publishCurrentState(
                feedback = resourceProvider.getString(R.string.practice_spelling_correct)
            )
            return
        }

        if (attemptCount >= 2) {
            currentResultState = SpellingResultState.REVEALED_WRONG
            completedCount += 1
            currentAnswer = answerWord
            hintLockedLength = answerWord.length
            publishCurrentState(
                feedback = resourceProvider.getString(
                    R.string.practice_spelling_revealed_answer,
                    answerWord
                )
            )
            return
        }

        currentResultState = SpellingResultState.RETRYABLE_WRONG
        publishCurrentState(
            feedback = buildRetryFeedback(input)
        )
    }

    fun nextWord() {
        if (!_uiState.value.canNext) return
        val nextIndex = resolveNextPracticeIndex(index, words.size)
        if (nextIndex == null) {
            currentResultState = SpellingResultState.COMPLETED
            _uiState.value = SpellingUiState(
                progressText = resourceProvider.getString(
                    R.string.practice_spelling_progress_text,
                    words.size,
                    words.size
                ),
                progressValue = words.size,
                progressMax = words.size.coerceAtLeast(1),
                resultState = SpellingResultState.COMPLETED,
                isCompleted = true,
                canEditAnswer = false,
                summary = currentSummary(),
                summaryText = sessionEngine.buildSummaryText(currentSummary())
            )
            return
        }
        index = nextIndex
        resetQuestionState()
        renderCurrent()
    }

    private fun loadWords(selectedIds: LongArray?, randomCount: Int) {
        viewModelScope.launch {
            words = wordProvider.loadWords(
                selectedIds = selectedIds,
                randomCount = randomCount,
                defaultLimit = 30
            )
            _sessionWordIds.value = words.map { it.id }
            index = 0
            completedCount = 0
            correctCount = 0
            submitCount = 0
            autoPlayRequestId = 0
            resetQuestionState()
            renderCurrent()
        }
    }

    private fun resetQuestionState() {
        answerWord = ""
        currentAnswer = ""
        hintLockedLength = 0
        attemptCount = 0
        hintCount = 0
        currentResultState = SpellingResultState.UNANSWERED
        letterPoolChars = emptyList()
    }

    private fun renderCurrent() {
        val word = words.getOrNull(index)
        if (word == null) {
            _uiState.value = SpellingUiState(
                meaning = resourceProvider.getString(R.string.practice_spelling_empty),
                wordLengthHint = resourceProvider.getString(R.string.practice_spelling_word_length_empty),
                progressText = resourceProvider.getString(
                    R.string.practice_spelling_progress_text,
                    0,
                    0
                ),
                progressValue = 0,
                progressMax = 1,
                canEditAnswer = false,
                summary = PracticeSessionSummary()
            )
            return
        }

        val token = ++renderToken
        answerWord = word.word.trim().uppercase(Locale.ROOT)
        letterPoolChars = sessionEngine.buildLetterPoolChars(answerWord)
        val cachedSpeech = assetLoader.cachedSpeech(word.id)
        val autoPlayForCachedSpeech = if (cachedSpeech != null) nextAutoPlayRequestId() else autoPlayRequestId
        publishCurrentState(
            meaning = assetLoader.cachedMeaning(word.id) ?: resourceProvider.getString(
                R.string.practice_spelling_meaning_loading
            ),
            speech = cachedSpeech,
            autoPlayRequestId = autoPlayForCachedSpeech,
            feedback = ""
        )

        viewModelScope.launch {
            val meaningDeferred = assetLoader.loadMeaningAsync(word)
            val speechDeferred = assetLoader.loadSpeechAsync(word)
            assetLoader.prefetch(words.getOrNull(index + 1))
            val meaning = runCatching { meaningDeferred.await() }.getOrDefault(
                resourceProvider.getString(R.string.practice_spelling_meaning_fallback)
            )
            val speech = runCatching { speechDeferred.await() }.getOrNull()
            if (token != renderToken) return@launch
            val nextAutoPlayRequestId = if (speech != null && _uiState.value.speech == null) {
                nextAutoPlayRequestId()
            } else {
                autoPlayRequestId
            }
            publishCurrentState(
                meaning = meaning,
                speech = speech,
                autoPlayRequestId = nextAutoPlayRequestId,
                feedback = ""
            )
        }
    }

    suspend fun ensureCurrentSpeech(): SpeechAudioSuccess? {
        val word = words.getOrNull(index) ?: return null
        val token = renderToken
        val speech = runCatching { assetLoader.loadSpeechAsync(word).await() }.getOrNull()
        if (token != renderToken || speech == null) return null
        if (_uiState.value.speech == null) {
            publishCurrentState(
                speech = speech,
                feedback = _uiState.value.feedback
            )
        }
        return speech
    }

    private fun publishCurrentState(
        meaning: String = _uiState.value.meaning,
        speech: SpeechAudioSuccess? = _uiState.value.speech,
        autoPlayRequestId: Int = this.autoPlayRequestId,
        feedback: String
    ) {
        val isCompleted = currentResultState == SpellingResultState.COMPLETED
        _uiState.value = SpellingUiState(
            meaning = meaning,
            wordLengthHint = if (answerWord.isBlank()) {
                resourceProvider.getString(R.string.practice_spelling_word_length_empty)
            } else {
                resourceProvider.getString(
                    R.string.practice_spelling_word_length_hint,
                    answerWord.length
                )
            },
            wordLength = answerWord.length,
            currentAnswer = currentAnswer,
            answerSlots = sessionEngine.buildAnswerSlots(answerWord, currentAnswer, hintLockedLength),
            letters = sessionEngine.buildLetterItems(letterPoolChars, currentAnswer),
            feedback = feedback,
            progressText = resourceProvider.getString(
                R.string.practice_spelling_progress_text,
                (index + 1).coerceAtMost(words.size),
                words.size
            ),
            progressValue = (index + 1).coerceAtMost(words.size),
            progressMax = words.size.coerceAtLeast(1),
            currentWord = words.getOrNull(index)?.word.orEmpty(),
            speech = speech,
            resultState = currentResultState,
            attemptCount = attemptCount,
            hintCount = hintCount,
            canSubmit = !isCompleted &&
                currentResultState != SpellingResultState.CORRECT &&
                currentResultState != SpellingResultState.REVEALED_WRONG &&
                currentAnswer.isNotBlank(),
            canNext = currentResultState == SpellingResultState.CORRECT ||
                currentResultState == SpellingResultState.REVEALED_WRONG,
            canHint = !isCompleted &&
                hintCount == 0 &&
                currentResultState != SpellingResultState.CORRECT &&
                currentResultState != SpellingResultState.REVEALED_WRONG &&
                hintLockedLength < answerWord.length,
            canEditAnswer = !isCompleted &&
                currentResultState != SpellingResultState.CORRECT &&
                currentResultState != SpellingResultState.REVEALED_WRONG,
            isCompleted = isCompleted,
            autoPlayRequestId = autoPlayRequestId,
            summary = currentSummary(),
            summaryText = if (isCompleted) sessionEngine.buildSummaryText(currentSummary()) else ""
        )
    }

    private fun appendAnswer(letter: Char) {
        if (_uiState.value.isCompleted || !_uiState.value.canEditAnswer) return
        if (currentAnswer.length >= answerWord.length) return
        currentAnswer += letter.toString().uppercase(Locale.ROOT)
        if (currentResultState == SpellingResultState.RETRYABLE_WRONG) {
            currentResultState = SpellingResultState.UNANSWERED
        }
        publishCurrentState(feedback = "")
    }

    private fun sanitizeAnswerInput(raw: String): String {
        return sessionEngine.sanitizeAnswerInput(answerWord, raw)
    }

    private fun nextAutoPlayRequestId(): Int {
        autoPlayRequestId += 1
        return autoPlayRequestId
    }

    private fun buildRetryFeedback(input: String): String {
        return sessionEngine.buildRetryFeedback(answerWord, input)
    }

    private fun currentSummary(): PracticeSessionSummary {
        return PracticeSessionSummary(
            questionCount = words.size,
            completedCount = completedCount,
            correctCount = correctCount,
            submitCount = submitCount
        )
    }

}
