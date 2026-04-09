package com.chen.memorizewords.feature.learning.ui.practice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chen.memorizewords.core.common.resource.ResourceProvider
import com.chen.memorizewords.feature.learning.R
import com.chen.memorizewords.domain.model.words.word.Word
import com.chen.memorizewords.domain.practice.PracticeWordProvider
import com.chen.memorizewords.domain.usecase.practice.EvaluateShadowingUseCase
import com.chen.memorizewords.domain.usecase.practice.SynthesizeSpeechUseCase
import com.chen.memorizewords.speech.api.ShadowingEvaluationResult
import com.chen.memorizewords.speech.api.SpeechAudioInput
import com.chen.memorizewords.speech.api.SpeechAudioSuccess
import com.chen.memorizewords.speech.api.SpeechFailureResult
import com.chen.memorizewords.speech.api.SpeechTask
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class ShadowingPracticeViewModel @Inject constructor(
    private val resourceProvider: ResourceProvider,
    private val synthesizeSpeech: SynthesizeSpeechUseCase,
    private val evaluateShadowing: EvaluateShadowingUseCase,
    private val wordProvider: PracticeWordProvider
) : ViewModel() {

    data class ShadowingUiState(
        val loading: Boolean = true,
        val questionIndex: Int = -1,
        val word: String = "",
        val speech: SpeechAudioSuccess? = null,
        val lastResult: ShadowingEvaluationResult? = null,
        val errorMessage: String = "",
        val isCompleted: Boolean = false,
        val evaluating: Boolean = false,
        val evaluationUnsupported: Boolean = false,
        val summary: PracticeSessionSummary = PracticeSessionSummary()
    )

    private val _uiState = MutableStateFlow(ShadowingUiState())
    val uiState: StateFlow<ShadowingUiState> = _uiState.asStateFlow()

    private val _sessionWordIds = MutableStateFlow<List<Long>>(emptyList())
    val sessionWordIds: StateFlow<List<Long>> = _sessionWordIds.asStateFlow()

    private var loadKey: String? = null
    private var words: List<Word> = emptyList()
    private var index: Int = 0
    private var renderRequestToken: Int = 0
    private var evaluationRequestToken: Int = 0

    fun loadWithSelection(selectedIds: LongArray?, randomCount: Int) {
        val newLoadKey = buildPracticeSelectionKey(selectedIds, randomCount)
        if (loadKey == newLoadKey) return
        loadKey = newLoadKey
        loadWords(selectedIds, randomCount)
    }

    fun nextWord() {
        if (words.isEmpty() || _uiState.value.isCompleted || _uiState.value.loading) return
        val nextSummary = recordShadowingCompletion(currentSummary(words.size), words.size)
        val nextIndex = resolveNextPracticeIndex(index, words.size)
        evaluationRequestToken = nextShadowingPracticeRequestToken(evaluationRequestToken)
        if (nextIndex == null) {
            renderRequestToken = nextShadowingPracticeRequestToken(renderRequestToken)
            _uiState.value = _uiState.value.copy(
                isCompleted = true,
                lastResult = null,
                errorMessage = "",
                evaluating = false,
                evaluationUnsupported = false,
                summary = nextSummary
            )
            return
        }
        index = nextIndex
        renderCurrentWord(summary = nextSummary)
    }

    fun evaluate(audioFilePath: String) {
        val currentState = _uiState.value
        if (currentState.isCompleted || currentState.loading || currentState.evaluating) return
        if (currentState.questionIndex != index) return
        val word = words.getOrNull(index)?.word.orEmpty()
        if (word.isBlank()) return
        val requestIndex = index
        val requestToken = nextShadowingPracticeRequestToken(evaluationRequestToken)
        evaluationRequestToken = requestToken
        viewModelScope.launch {
            val nextSummary = recordShadowingSubmission(currentSummary(words.size), words.size)
            _uiState.value = _uiState.value.copy(
                lastResult = null,
                errorMessage = "",
                evaluating = true,
                evaluationUnsupported = false,
                summary = nextSummary
            )
            val result = withContext(Dispatchers.IO) {
                evaluateShadowing(
                    SpeechTask.EvaluateShadowing(
                        referenceText = word,
                        audioInput = SpeechAudioInput.FileInput(audioFilePath),
                    )
                )
            }
            if (!shouldApplyShadowingQuestionResult(
                    activeIndex = index,
                    activeToken = evaluationRequestToken,
                    resultIndex = requestIndex,
                    resultToken = requestToken
                )
            ) {
                return@launch
            }
            _uiState.value = when (result) {
                is ShadowingEvaluationResult -> _uiState.value.copy(
                    lastResult = result,
                    errorMessage = "",
                    evaluating = false,
                    evaluationUnsupported = false,
                    summary = nextSummary
                )

                is SpeechFailureResult -> _uiState.value.copy(
                    lastResult = null,
                    errorMessage = result.message
                        ?: resourceProvider.getString(R.string.practice_shadowing_scoring_unavailable),
                    evaluating = false,
                    evaluationUnsupported = true,
                    summary = nextSummary
                )

                else -> _uiState.value.copy(evaluating = false)
            }
        }
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
            renderRequestToken = nextShadowingPracticeRequestToken(renderRequestToken)
            evaluationRequestToken = nextShadowingPracticeRequestToken(evaluationRequestToken)
            renderCurrentWord(summary = PracticeSessionSummary(questionCount = words.size))
        }
    }

    private fun renderCurrentWord(summary: PracticeSessionSummary = currentSummary(words.size)) {
        val current = words.getOrNull(index)
        if (current == null) {
            renderRequestToken = nextShadowingPracticeRequestToken(renderRequestToken)
            evaluationRequestToken = nextShadowingPracticeRequestToken(evaluationRequestToken)
            _uiState.value = ShadowingUiState(
                loading = false,
                word = resourceProvider.getString(R.string.practice_shadowing_empty),
                isCompleted = true,
                summary = PracticeSessionSummary()
            )
            return
        }

        val requestIndex = index
        val requestToken = nextShadowingPracticeRequestToken(renderRequestToken)
        renderRequestToken = requestToken
        _uiState.value = ShadowingUiState(
            loading = true,
            questionIndex = requestIndex,
            word = current.word,
            speech = null,
            lastResult = null,
            errorMessage = "",
            isCompleted = false,
            evaluating = false,
            evaluationUnsupported = false,
            summary = summary
        )
        viewModelScope.launch {
            val speech = synthesizeSpeech(
                SpeechTask.SynthesizeWord(
                    text = current.word
                )
            ) as? SpeechAudioSuccess
            if (!shouldApplyShadowingQuestionResult(
                    activeIndex = index,
                    activeToken = renderRequestToken,
                    resultIndex = requestIndex,
                    resultToken = requestToken
                )
            ) {
                return@launch
            }
            _uiState.value = ShadowingUiState(
                loading = false,
                questionIndex = requestIndex,
                word = current.word,
                speech = speech,
                lastResult = null,
                errorMessage = "",
                isCompleted = false,
                evaluating = false,
                evaluationUnsupported = false,
                summary = summary
            )
        }
    }

    private fun currentSummary(questionCount: Int): PracticeSessionSummary {
        val summary = _uiState.value.summary
        return if (summary.questionCount == questionCount) {
            summary
        } else {
            PracticeSessionSummary(questionCount = questionCount)
        }
    }
}

internal fun nextShadowingPracticeRequestToken(currentToken: Int): Int {
    return currentToken + 1
}

internal fun shouldApplyShadowingQuestionResult(
    activeIndex: Int,
    activeToken: Int,
    resultIndex: Int,
    resultToken: Int
): Boolean {
    return activeIndex == resultIndex && activeToken == resultToken
}

internal fun recordShadowingSubmission(
    summary: PracticeSessionSummary,
    questionCount: Int
): PracticeSessionSummary {
    val safeQuestionCount = questionCount.coerceAtLeast(0)
    return summary.copy(
        questionCount = safeQuestionCount,
        submitCount = (summary.submitCount + 1).coerceAtMost(safeQuestionCount)
    )
}

internal fun recordShadowingCompletion(
    summary: PracticeSessionSummary,
    questionCount: Int
): PracticeSessionSummary {
    val safeQuestionCount = questionCount.coerceAtLeast(0)
    return summary.copy(
        questionCount = safeQuestionCount,
        completedCount = (summary.completedCount + 1).coerceAtMost(safeQuestionCount)
    )
}
