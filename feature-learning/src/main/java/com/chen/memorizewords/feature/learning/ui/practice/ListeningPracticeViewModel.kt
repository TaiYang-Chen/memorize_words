package com.chen.memorizewords.feature.learning.ui.practice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chen.memorizewords.core.common.resource.ResourceProvider
import com.chen.memorizewords.feature.learning.R
import com.chen.memorizewords.domain.query.word.WordReadFacade
import com.chen.memorizewords.domain.model.words.word.Word
import com.chen.memorizewords.domain.practice.PracticeWordProvider
import com.chen.memorizewords.domain.usecase.practice.SynthesizeSpeechUseCase
import com.chen.memorizewords.speech.api.SpeechAudioSuccess
import com.chen.memorizewords.speech.api.SpeechTask
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class ListeningPracticeViewModel @Inject constructor(
    private val resourceProvider: ResourceProvider,
    private val wordReadFacade: WordReadFacade,
    private val synthesizeSpeech: SynthesizeSpeechUseCase,
    private val wordProvider: PracticeWordProvider
) : ViewModel() {

    data class ListeningOption(
        val text: String,
        val correct: Boolean
    )

    data class ListeningUiState(
        val loading: Boolean = true,
        val questionLoading: Boolean = true,
        val questionIndex: Int = -1,
        val progress: String = "0/0",
        val options: List<ListeningOption> = emptyList(),
        val selectedIndex: Int? = null,
        val feedback: String = "",
        val canNext: Boolean = false,
        val isCompleted: Boolean = false,
        val currentWord: String = "",
        val speech: SpeechAudioSuccess? = null,
        val summary: PracticeSessionSummary = PracticeSessionSummary()
    )

    private data class ListeningQuestion(
        val word: Word,
        val options: List<ListeningOption>
    )

    private val _uiState = MutableStateFlow(ListeningUiState())
    val uiState: StateFlow<ListeningUiState> = _uiState.asStateFlow()

    private val _sessionWordIds = MutableStateFlow<List<Long>>(emptyList())
    val sessionWordIds: StateFlow<List<Long>> = _sessionWordIds.asStateFlow()

    private var loadKey: String? = null
    private val questions = mutableListOf<ListeningQuestion>()
    private var currentIndex: Int = 0
    private var questionRequestToken: Int = 0

    fun loadWithSelection(selectedIds: LongArray?, randomCount: Int) {
        val newLoadKey = buildPracticeSelectionKey(selectedIds, randomCount)
        if (loadKey == newLoadKey) return
        loadKey = newLoadKey
        loadQuestions(selectedIds, randomCount)
    }

    fun onSelect(index: Int) {
        val current = _uiState.value
        if (current.questionLoading || current.questionIndex != currentIndex) return
        if (current.selectedIndex != null || current.isCompleted) return
        val option = current.options.getOrNull(index) ?: return
        _uiState.value = current.copy(
            selectedIndex = index,
            feedback = resourceProvider.getString(
                if (option.correct) {
                    R.string.practice_listening_feedback_correct
                } else {
                    R.string.practice_listening_feedback_wrong
                }
            ),
            canNext = true,
            summary = recordListeningAnswer(
                summary = currentSummary(),
                questionCount = questions.size,
                isCorrect = option.correct
            )
        )
    }

    fun onNext() {
        val current = _uiState.value
        if (current.questionLoading || current.isCompleted || !current.canNext) return
        if (questions.isEmpty()) return
        val nextIndex = resolveNextPracticeIndex(
            currentIndex = currentIndex,
            totalCount = questions.size
        )
        if (nextIndex == null) {
            questionRequestToken = nextListeningPracticeRequestToken(questionRequestToken)
            _uiState.value = _uiState.value.copy(
                feedback = resourceProvider.getString(R.string.practice_listening_feedback_completed),
                canNext = false,
                isCompleted = true,
                questionLoading = false,
                summary = currentSummary()
            )
            return
        }
        currentIndex = nextIndex
        renderCurrentQuestion()
    }

    fun currentWord(): String = questions.getOrNull(currentIndex)?.word?.word.orEmpty()

    private fun loadQuestions(selectedIds: LongArray?, randomCount: Int) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                loading = true,
                questionLoading = true,
                questionIndex = -1,
                options = emptyList(),
                selectedIndex = null,
                feedback = "",
                canNext = false,
                currentWord = "",
                speech = null,
                summary = PracticeSessionSummary()
            )
            val words = wordProvider.loadWords(
                selectedIds = selectedIds,
                randomCount = randomCount,
                defaultLimit = 20
            )
            val built = words.mapNotNull { word ->
                val definitions = wordReadFacade.generateMultipleChoiceOptions(word.id)
                if (definitions.isEmpty()) return@mapNotNull null
                val options = definitions
                    .take(4)
                    .map {
                        ListeningOption(
                            text = "${it.partOfSpeech.abbr} ${it.meaningChinese}",
                            correct = it.wordId == word.id
                        )
                    }
                    .shuffled()
                if (options.none { it.correct }) return@mapNotNull null
                ListeningQuestion(word = word, options = options)
            }
            questions.clear()
            questions.addAll(built)
            _sessionWordIds.value = built.map { it.word.id }.distinct()
            currentIndex = 0
            renderCurrentQuestion()
        }
    }

    private fun renderCurrentQuestion() {
        val question = questions.getOrNull(currentIndex)
        if (question == null) {
            questionRequestToken = nextListeningPracticeRequestToken(questionRequestToken)
            _uiState.value = ListeningUiState(
                loading = false,
                questionLoading = false,
                progress = "0/0",
                feedback = resourceProvider.getString(R.string.practice_listening_feedback_empty),
                isCompleted = true,
                summary = PracticeSessionSummary()
            )
            return
        }

        val requestIndex = currentIndex
        val requestToken = nextListeningPracticeRequestToken(questionRequestToken)
        questionRequestToken = requestToken
        _uiState.value = buildListeningQuestionLoadingState(
            current = _uiState.value,
            questionIndex = requestIndex,
            progress = "${requestIndex + 1}/${questions.size}",
            currentWord = question.word.word,
            summary = currentSummary()
        )
        viewModelScope.launch {
            val speech = synthesizeSpeech(
                SpeechTask.SynthesizeWord(
                    text = question.word.word
                )
            ) as? SpeechAudioSuccess
            if (!shouldApplyListeningQuestionResult(
                    activeIndex = currentIndex,
                    activeToken = questionRequestToken,
                    resultIndex = requestIndex,
                    resultToken = requestToken
                )
            ) {
                return@launch
            }
            _uiState.value = ListeningUiState(
                loading = false,
                questionLoading = false,
                questionIndex = requestIndex,
                progress = "${requestIndex + 1}/${questions.size}",
                options = question.options,
                selectedIndex = null,
                feedback = "",
                canNext = false,
                isCompleted = false,
                currentWord = question.word.word,
                speech = speech,
                summary = currentSummary()
            )
        }
    }

    private fun currentSummary(): PracticeSessionSummary {
        val summary = _uiState.value.summary
        return if (summary.questionCount == questions.size) {
            summary
        } else {
            PracticeSessionSummary(questionCount = questions.size)
        }
    }
}

internal fun buildListeningQuestionLoadingState(
    current: ListeningPracticeViewModel.ListeningUiState,
    questionIndex: Int,
    progress: String,
    currentWord: String,
    summary: PracticeSessionSummary
): ListeningPracticeViewModel.ListeningUiState {
    return current.copy(
        loading = true,
        questionLoading = true,
        questionIndex = questionIndex,
        progress = progress,
        options = emptyList(),
        selectedIndex = null,
        feedback = "",
        canNext = false,
        isCompleted = false,
        currentWord = currentWord,
        speech = null,
        summary = summary
    )
}

internal fun nextListeningPracticeRequestToken(currentToken: Int): Int {
    return currentToken + 1
}

internal fun shouldApplyListeningQuestionResult(
    activeIndex: Int,
    activeToken: Int,
    resultIndex: Int,
    resultToken: Int
): Boolean {
    return activeIndex == resultIndex && activeToken == resultToken
}

internal fun recordListeningAnswer(
    summary: PracticeSessionSummary,
    questionCount: Int,
    isCorrect: Boolean
): PracticeSessionSummary {
    val safeQuestionCount = questionCount.coerceAtLeast(0)
    val nextCompletedCount = (summary.completedCount + 1).coerceAtMost(safeQuestionCount)
    val nextCorrectCount = (
        summary.correctCount + if (isCorrect) 1 else 0
        ).coerceAtMost(nextCompletedCount)
    val nextSubmitCount = (summary.submitCount + 1).coerceAtMost(safeQuestionCount)
    return PracticeSessionSummary(
        questionCount = safeQuestionCount,
        completedCount = nextCompletedCount,
        correctCount = nextCorrectCount,
        submitCount = nextSubmitCount
    )
}
