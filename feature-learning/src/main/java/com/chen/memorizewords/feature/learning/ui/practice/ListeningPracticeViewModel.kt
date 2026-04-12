package com.chen.memorizewords.feature.learning.ui.practice

import androidx.annotation.StringRes
import androidx.lifecycle.viewModelScope
import com.chen.memorizewords.core.common.resource.ResourceProvider
import com.chen.memorizewords.core.ui.vm.BaseViewModel
import com.chen.memorizewords.domain.model.study.progress.word.WordLearningState
import com.chen.memorizewords.domain.model.words.word.Word
import com.chen.memorizewords.domain.model.words.word.WordDefinitions
import com.chen.memorizewords.domain.model.words.word.WordExample
import com.chen.memorizewords.domain.practice.PracticeWordProvider
import com.chen.memorizewords.domain.query.word.WordReadFacade
import com.chen.memorizewords.domain.usecase.practice.SynthesizeSpeechUseCase
import com.chen.memorizewords.domain.usecase.word.study.GetWordLearningStatesByBookIdUseCase
import com.chen.memorizewords.feature.learning.R
import com.chen.memorizewords.speech.api.SpeechAudioSuccess
import com.chen.memorizewords.speech.api.SpeechTask
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.ArrayDeque
import javax.inject.Inject
import kotlin.math.roundToInt
import kotlin.random.Random
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope

enum class ListeningPracticeMode(
    @param:StringRes val titleRes: Int,
    @param:StringRes val descriptionRes: Int
) {
    RANDOM(
        titleRes = R.string.practice_listening_mode_random,
        descriptionRes = R.string.practice_listening_mode_random_desc
    ),
    SPELLING(
        titleRes = R.string.practice_listening_mode_spelling,
        descriptionRes = R.string.practice_listening_mode_spelling_desc
    ),
    MEANING(
        titleRes = R.string.practice_listening_mode_meaning,
        descriptionRes = R.string.practice_listening_mode_meaning_desc
    ),
    MIXED(
        titleRes = R.string.practice_listening_mode_mixed,
        descriptionRes = R.string.practice_listening_mode_mixed_desc
    ),
    ADVANCED_REVIEW(
        titleRes = R.string.practice_listening_mode_advanced,
        descriptionRes = R.string.practice_listening_mode_advanced_desc
    )
}

enum class ListeningQuestionType {
    MEANING,
    SPELLING
}

private enum class ListeningScreen {
    PRACTICE,
    STUDY,
    REPORT
}

private enum class QueueType {
    NEW,
    REVIEW
}

enum class ListeningFeedbackTone {
    NONE,
    INFO,
    SUCCESS,
    ERROR
}

data class ListeningMeaningOptionUi(
    val id: Long,
    val partOfSpeech: String,
    val content: String,
    val isCorrect: Boolean
)

data class ListeningStudyDefinitionUi(
    val partOfSpeech: String,
    val meaning: String
)

data class ListeningReportWordUi(
    val word: String,
    val progressText: String,
    val isCompleted: Boolean
)

data class ListeningReportUi(
    val accuracyText: String = "0%",
    val reviewedCountText: String = "0",
    val skippedCountText: String = "0",
    val reviewedWords: List<ListeningReportWordUi> = emptyList(),
    val unfinishedWords: List<ListeningReportWordUi> = emptyList(),
    val summaryText: String = ""
)

@HiltViewModel
class ListeningPracticeViewModel @Inject constructor(
    private val resourceProvider: ResourceProvider,
    private val wordReadFacade: WordReadFacade,
    private val synthesizeSpeech: SynthesizeSpeechUseCase,
    private val wordProvider: PracticeWordProvider,
    private val getWordLearningStatesByBookId: GetWordLearningStatesByBookIdUseCase
) : BaseViewModel() {

    data class ListeningUiState(
        val loading: Boolean = false,
        val hasStarted: Boolean = false,
        val mode: ListeningPracticeMode = ListeningPracticeMode.RANDOM,
        val modeTitle: String = "",
        val modeDescription: String = "",
        val headerProgressText: String = "0/0",
        val progressValue: Int = 0,
        val progressMax: Int = 1,
        val reviewProgressText: String = "",
        val reviewIndicatorText: String = "",
        val promptText: String = "",
        val promptHint: String = "",
        val phoneticChipText: String = "",
        val feedbackMessage: String = "",
        val feedbackTone: ListeningFeedbackTone = ListeningFeedbackTone.NONE,
        val questionType: ListeningQuestionType = ListeningQuestionType.MEANING,
        val meaningOptions: List<ListeningMeaningOptionUi> = emptyList(),
        val selectedMeaningIndex: Int? = null,
        val spellingInput: String = "",
        val spellingSubmitEnabled: Boolean = false,
        val bottomActionVisible: Boolean = false,
        val showMeaningQuestion: Boolean = false,
        val showSpellingQuestion: Boolean = false,
        val showStudyState: Boolean = false,
        val showReportState: Boolean = false,
        val studyWord: String = "",
        val studyPhoneticChipText: String = "",
        val studyDefinitions: List<ListeningStudyDefinitionUi> = emptyList(),
        val studyExampleEnglish: String = "",
        val studyExampleChinese: String = "",
        val studyReviewStatusText: String = "",
        val primaryButtonText: String = "",
        val primaryButtonEnabled: Boolean = false,
        val report: ListeningReportUi = ListeningReportUi(),
        val speech: SpeechAudioSuccess? = null,
        val autoPlayRequestId: Int = 0,
        val summary: PracticeSessionSummary = PracticeSessionSummary()
    )

    private data class WordRuntime(
        val word: Word,
        val definitions: List<WordDefinitions>,
        val examples: List<WordExample>,
        val meaningOptions: List<ListeningMeaningOptionUi>,
        val learningState: WordLearningState?,
        var reviewRequired: Boolean = false,
        var reviewEnqueued: Boolean = false,
        var enteredReview: Boolean = false,
        var mastered: Boolean = false,
        var consecutiveCorrect: Int = 0,
        var wrongCount: Int = 0
    )

    private data class ActiveCard(
        val wordId: Long,
        val queueType: QueueType,
        val questionType: ListeningQuestionType
    )

    private val _uiState = MutableStateFlow(initialState())
    val uiState: StateFlow<ListeningUiState> = _uiState.asStateFlow()

    private val _sessionWordIds = MutableStateFlow<List<Long>>(emptyList())
    val sessionWordIds: StateFlow<List<Long>> = _sessionWordIds.asStateFlow()

    private val runtimes = linkedMapOf<Long, WordRuntime>()
    private val newQueue = ArrayDeque<Long>()
    private val reviewQueue = ArrayDeque<Long>()
    private val speechCache = mutableMapOf<Long, SpeechAudioSuccess?>()

    private var selectedWordIds: LongArray? = null
    private var selectedRandomCount: Int = 20
    private var loadKey: String? = null
    private var activeCard: ActiveCard? = null
    private var currentScreen: ListeningScreen = ListeningScreen.PRACTICE
    private var currentSpeechWordId: Long? = null
    private var speechRequestToken: Int = 0
    private var autoPlayRequestId: Int = 0
    private var mixedModeToggle: Boolean = false
    private var lastQueueType: QueueType? = null
    private var correctAttemptCount: Int = 0
    private var gradedAttemptCount: Int = 0
    private var skippedCount: Int = 0
    private var autoAdvanceJob: Job? = null

    fun loadWithSelection(selectedIds: LongArray?, randomCount: Int) {
        selectedWordIds = selectedIds?.clone()
        selectedRandomCount = randomCount
    }

    fun startSession(mode: ListeningPracticeMode) {
        val selectedIds = selectedWordIds?.clone()
        val randomCount = selectedRandomCount
        val newLoadKey = "${buildPracticeSelectionKey(selectedIds, randomCount)}_${mode.name}"
        if (loadKey == newLoadKey && _uiState.value.hasStarted) return
        loadKey = newLoadKey
        autoAdvanceJob?.cancel()
        viewModelScope.launch {
            _uiState.value = initialState().copy(
                loading = true,
                hasStarted = true,
                mode = mode,
                modeTitle = resourceProvider.getString(mode.titleRes),
                modeDescription = resourceProvider.getString(mode.descriptionRes),
                headerProgressText = resourceProvider.getString(
                    R.string.practice_listening_progress_format,
                    0,
                    0
                ),
                promptText = resourceProvider.getString(R.string.practice_listening_loading)
            )

            val words = wordProvider.loadWords(
                selectedIds = selectedIds,
                randomCount = randomCount,
                defaultLimit = 20
            )
            val statesByWordId = loadLearningStatesByWordId()
            val orderedWords = orderWordsForMode(words, statesByWordId, mode)

            runtimes.clear()
            newQueue.clear()
            reviewQueue.clear()
            speechCache.clear()
            activeCard = null
            currentSpeechWordId = null
            mixedModeToggle = false
            lastQueueType = null
            correctAttemptCount = 0
            gradedAttemptCount = 0
            skippedCount = 0
            autoPlayRequestId = 0

            val runtimeList = buildRuntimeList(orderedWords, statesByWordId)
            runtimeList.forEach { runtime ->
                runtimes[runtime.word.id] = runtime
                newQueue.addLast(runtime.word.id)
            }
            _sessionWordIds.value = runtimeList.map { it.word.id }.distinct()

            if (runtimeList.isEmpty()) {
                publishReport(
                    emptySummary = resourceProvider.getString(R.string.practice_listening_empty_report)
                )
                return@launch
            }
            moveToNextQuestion()
        }
    }

    fun onMeaningOptionSelected(index: Int) {
        val state = _uiState.value
        val card = activeCard ?: return
        if (currentScreen != ListeningScreen.PRACTICE) return
        if (card.questionType != ListeningQuestionType.MEANING) return
        if (state.selectedMeaningIndex != null) return
        val runtime = runtimes[card.wordId] ?: return
        val option = runtime.meaningOptions.getOrNull(index) ?: return
        if (option.isCorrect) {
            handleCorrectAnswer(runtime, card, selectedIndex = index)
        } else {
            handleWrongAnswer(runtime, card, selectedIndex = index)
        }
    }

    fun onSpellingInputChanged(input: String) {
        if (currentScreen != ListeningScreen.PRACTICE) return
        if (activeCard?.questionType != ListeningQuestionType.SPELLING) return
        val sanitized = sanitizeSpellingInput(input)
        val current = _uiState.value
        if (current.spellingInput == sanitized) return
        _uiState.value = current.copy(
            spellingInput = sanitized,
            spellingSubmitEnabled = sanitized.isNotBlank(),
            feedbackMessage = ""
        )
    }

    fun submitSpellingAnswer() {
        val card = activeCard ?: return
        if (card.questionType != ListeningQuestionType.SPELLING) return
        if (currentScreen != ListeningScreen.PRACTICE) return
        val runtime = runtimes[card.wordId] ?: return
        val input = _uiState.value.spellingInput
        if (input.isBlank()) return
        if (normalizeAnswer(input) == normalizeAnswer(runtime.word.word)) {
            handleCorrectAnswer(runtime, card)
        } else {
            handleWrongAnswer(runtime, card)
        }
    }

    fun onRevealAnswer() {
        val card = activeCard ?: return
        val runtime = runtimes[card.wordId] ?: return
        if (currentScreen != ListeningScreen.PRACTICE) return
        handleWrongAnswer(runtime, card, viewedAnswer = true)
    }

    fun onSkipQuestion() {
        val runtime = activeCard?.let { runtimes[it.wordId] } ?: return
        if (currentScreen != ListeningScreen.PRACTICE) return
        skippedCount += 1
        runtime.reviewRequired = true
        runtime.enteredReview = true
        enqueueForReview(runtime.word.id)
        moveToNextQuestion(
            feedbackMessage = resourceProvider.getString(R.string.practice_listening_feedback_skipped),
            feedbackTone = ListeningFeedbackTone.INFO
        )
    }

    fun onContinueAfterStudy() {
        if (currentScreen != ListeningScreen.STUDY) return
        moveToNextQuestion()
    }

    suspend fun ensureCurrentSpeech(): SpeechAudioSuccess? {
        val wordId = when (currentScreen) {
            ListeningScreen.PRACTICE -> activeCard?.wordId
            ListeningScreen.STUDY -> activeCard?.wordId
            ListeningScreen.REPORT -> null
        } ?: return null
        val cached = speechCache[wordId]
        if (cached != null) return cached
        val runtime = runtimes[wordId] ?: return null
        val speech = synthesizeSpeech(
            SpeechTask.SynthesizeWord(
                text = runtime.word.word,
                locale = "en-US"
            )
        ) as? SpeechAudioSuccess
        speechCache[wordId] = speech
        if (currentSpeechWordId == wordId && speech != null) {
            _uiState.value = _uiState.value.copy(speech = speech)
        }
        return speech
    }

    private suspend fun loadLearningStatesByWordId(): Map<Long, WordLearningState> {
        val bookId = wordProvider.resolveBookId() ?: return emptyMap()
        return getWordLearningStatesByBookId(bookId).associateBy { it.wordId }
    }

    private suspend fun buildRuntimeList(
        words: List<Word>,
        statesByWordId: Map<Long, WordLearningState>
    ): List<WordRuntime> = supervisorScope {
        words.map { word ->
            async {
                val definitions = wordReadFacade.getWordDefinitions(word.id)
                val examples = wordReadFacade.getWordExamples(word.id)
                val meaningOptions = buildMeaningOptions(word.id)
                WordRuntime(
                    word = word,
                    definitions = definitions,
                    examples = examples,
                    meaningOptions = meaningOptions,
                    learningState = statesByWordId[word.id]
                )
            }
        }.awaitAll()
    }

    private suspend fun buildMeaningOptions(wordId: Long): List<ListeningMeaningOptionUi> {
        return wordReadFacade.generateMultipleChoiceOptions(wordId)
            .take(4)
            .map {
                ListeningMeaningOptionUi(
                    id = it.id,
                    partOfSpeech = it.partOfSpeech.abbr,
                    content = it.meaningChinese,
                    isCorrect = it.wordId == wordId
                )
            }
            .shuffled(Random(wordId))
            .takeIf { options -> options.size >= 2 && options.any { it.isCorrect } }
            .orEmpty()
    }

    private fun orderWordsForMode(
        words: List<Word>,
        statesByWordId: Map<Long, WordLearningState>,
        mode: ListeningPracticeMode
    ): List<Word> {
        if (mode != ListeningPracticeMode.ADVANCED_REVIEW) return words
        val now = System.currentTimeMillis()
        return words.sortedWith(
            compareByDescending<Word> { word ->
                advancedPriorityScore(statesByWordId[word.id], now)
            }.thenBy { it.word.lowercase() }
        )
    }

    private fun advancedPriorityScore(
        state: WordLearningState?,
        now: Long
    ): Int {
        if (state == null) return 10
        val reviewDueBonus = if (state.nextReviewTime in 1..now) 60 else 0
        val masteryRisk = (5 - state.masteryLevel.coerceIn(0, 5)) * 10
        val userStatusBonus = if (state.userStatus == 1) -20 else 10
        val overdueDays = if (state.nextReviewTime in 1..now) {
            ((now - state.nextReviewTime) / (24 * 60 * 60 * 1000L)).toInt().coerceAtMost(30)
        } else {
            0
        }
        return reviewDueBonus + masteryRisk + userStatusBonus + overdueDays
    }

    private fun moveToNextQuestion(
        feedbackMessage: String = "",
        feedbackTone: ListeningFeedbackTone = ListeningFeedbackTone.NONE
    ) {
        autoAdvanceJob?.cancel()
        val nextWordId = selectNextWordId()
        if (nextWordId == null) {
            publishReport()
            return
        }
        val runtime = runtimes[nextWordId] ?: run {
            publishReport()
            return
        }
        val queueType = if (runtime.reviewRequired) QueueType.REVIEW else QueueType.NEW
        val questionType = resolveQuestionType(runtime)
        activeCard = ActiveCard(
            wordId = nextWordId,
            queueType = queueType,
            questionType = questionType
        )
        if (queueType == QueueType.REVIEW) {
            runtime.reviewEnqueued = false
        }
        currentScreen = ListeningScreen.PRACTICE
        renderPracticeState(runtime, questionType, feedbackMessage, feedbackTone)
    }

    private fun selectNextWordId(): Long? {
        val hasNew = newQueue.isNotEmpty()
        val hasReview = reviewQueue.isNotEmpty()
        if (!hasNew && !hasReview) return null
        val queueType = when {
            hasNew && hasReview -> {
                if (lastQueueType == QueueType.NEW) QueueType.REVIEW else QueueType.NEW
            }

            hasNew -> QueueType.NEW
            else -> QueueType.REVIEW
        }
        lastQueueType = queueType
        return when (queueType) {
            QueueType.NEW -> newQueue.removeFirst()
            QueueType.REVIEW -> reviewQueue.removeFirst()
        }
    }

    private fun resolveQuestionType(runtime: WordRuntime): ListeningQuestionType {
        val canMeaning = runtime.meaningOptions.isNotEmpty()
        val canSpelling = runtime.word.word.isNotBlank()
        return when (_uiState.value.mode) {
            ListeningPracticeMode.MEANING -> {
                if (canMeaning) ListeningQuestionType.MEANING else ListeningQuestionType.SPELLING
            }

            ListeningPracticeMode.SPELLING -> ListeningQuestionType.SPELLING

            ListeningPracticeMode.MIXED -> {
                mixedModeToggle = !mixedModeToggle
                val preferred = if (mixedModeToggle) {
                    ListeningQuestionType.MEANING
                } else {
                    ListeningQuestionType.SPELLING
                }
                preferred.availableOrFallback(canMeaning, canSpelling)
            }

            ListeningPracticeMode.RANDOM -> {
                val preferred = if (Random.nextBoolean()) {
                    ListeningQuestionType.MEANING
                } else {
                    ListeningQuestionType.SPELLING
                }
                preferred.availableOrFallback(canMeaning, canSpelling)
            }

            ListeningPracticeMode.ADVANCED_REVIEW -> {
                val preferred = when {
                    runtime.reviewRequired && runtime.wrongCount >= 2 -> ListeningQuestionType.SPELLING
                    runtime.reviewRequired -> ListeningQuestionType.MEANING
                    else -> ListeningQuestionType.SPELLING
                }
                preferred.availableOrFallback(canMeaning, canSpelling)
            }
        }
    }

    private fun ListeningQuestionType.availableOrFallback(
        canMeaning: Boolean,
        canSpelling: Boolean
    ): ListeningQuestionType {
        return when (this) {
            ListeningQuestionType.MEANING -> {
                if (canMeaning) ListeningQuestionType.MEANING else ListeningQuestionType.SPELLING
            }

            ListeningQuestionType.SPELLING -> {
                if (canSpelling) ListeningQuestionType.SPELLING else ListeningQuestionType.MEANING
            }
        }
    }

    private fun renderPracticeState(
        runtime: WordRuntime,
        questionType: ListeningQuestionType,
        feedbackMessage: String,
        feedbackTone: ListeningFeedbackTone
    ) {
        currentSpeechWordId = runtime.word.id
        _uiState.value = ListeningUiState(
            loading = false,
            hasStarted = true,
            mode = _uiState.value.mode,
            modeTitle = resourceProvider.getString(_uiState.value.mode.titleRes),
            modeDescription = resourceProvider.getString(_uiState.value.mode.descriptionRes),
            headerProgressText = resourceProvider.getString(
                R.string.practice_listening_progress_format,
                runtimes.values.count { it.mastered },
                runtimes.size
            ),
            progressValue = runtimes.values.count { it.mastered },
            progressMax = runtimes.size.coerceAtLeast(1),
            reviewProgressText = resourceProvider.getString(
                R.string.practice_listening_review_progress_format,
                runtimes.values.count { it.reviewRequired },
                runtimes.size
            ),
            reviewIndicatorText = if (runtime.reviewRequired) {
                resourceProvider.getString(
                    R.string.practice_listening_review_badge,
                    runtime.consecutiveCorrect,
                    REVIEW_TARGET
                )
            } else {
                resourceProvider.getString(R.string.practice_listening_new_word_badge)
            },
            promptText = if (questionType == ListeningQuestionType.MEANING) {
                resourceProvider.getString(R.string.practice_listening_prompt_meaning)
            } else {
                resourceProvider.getString(R.string.practice_listening_prompt_spelling)
            },
            promptHint = if (questionType == ListeningQuestionType.SPELLING) {
                resourceProvider.getString(R.string.practice_listening_prompt_spelling_hint)
            } else {
                ""
            },
            phoneticChipText = buildPhoneticChip(runtime.word),
            feedbackMessage = feedbackMessage,
            feedbackTone = feedbackTone,
            questionType = questionType,
            meaningOptions = runtime.meaningOptions,
            selectedMeaningIndex = null,
            spellingInput = "",
            spellingSubmitEnabled = false,
            bottomActionVisible = true,
            showMeaningQuestion = questionType == ListeningQuestionType.MEANING,
            showSpellingQuestion = questionType == ListeningQuestionType.SPELLING,
            showStudyState = false,
            showReportState = false,
            primaryButtonText = "",
            primaryButtonEnabled = false,
            speech = speechCache[runtime.word.id],
            autoPlayRequestId = nextAutoPlayRequestIdIfNeeded(speechCache[runtime.word.id]),
            summary = buildSummary()
        )
        requestSpeech(runtime.word)
    }

    private fun handleCorrectAnswer(
        runtime: WordRuntime,
        card: ActiveCard,
        selectedIndex: Int? = null
    ) {
        gradedAttemptCount += 1
        correctAttemptCount += 1
        val masteredNow = if (card.queueType == QueueType.REVIEW || runtime.reviewRequired) {
            runtime.consecutiveCorrect += 1
            if (runtime.consecutiveCorrect >= REVIEW_TARGET) {
                runtime.reviewRequired = false
                runtime.reviewEnqueued = false
                runtime.mastered = true
                true
            } else {
                enqueueForReview(runtime.word.id)
                false
            }
        } else {
            runtime.mastered = true
            true
        }
        _uiState.value = _uiState.value.copy(
            selectedMeaningIndex = selectedIndex,
            feedbackMessage = if (masteredNow) {
                resourceProvider.getString(R.string.practice_listening_feedback_correct)
            } else {
                resourceProvider.getString(
                    R.string.practice_listening_feedback_review_progress,
                    runtime.consecutiveCorrect,
                    REVIEW_TARGET
                )
            },
            feedbackTone = ListeningFeedbackTone.SUCCESS,
            summary = buildSummary()
        )
        autoAdvanceJob = viewModelScope.launch {
            delay(CORRECT_FEEDBACK_DURATION_MS)
            if (activeCard?.wordId != runtime.word.id || currentScreen != ListeningScreen.PRACTICE) {
                return@launch
            }
            moveToNextQuestion()
        }
    }

    private fun handleWrongAnswer(
        runtime: WordRuntime,
        card: ActiveCard,
        selectedIndex: Int? = null,
        viewedAnswer: Boolean = false
    ) {
        autoAdvanceJob?.cancel()
        gradedAttemptCount += 1
        runtime.wrongCount += 1
        runtime.enteredReview = true
        runtime.reviewRequired = true
        runtime.mastered = false
        runtime.consecutiveCorrect = 0
        enqueueForReview(runtime.word.id, prioritize = runtime.wrongCount >= 2)
        activeCard = card
        currentScreen = ListeningScreen.STUDY
        currentSpeechWordId = runtime.word.id
        _uiState.value = _uiState.value.copy(
            headerProgressText = resourceProvider.getString(
                R.string.practice_listening_progress_format,
                runtimes.values.count { it.mastered },
                runtimes.size
            ),
            progressValue = runtimes.values.count { it.mastered },
            progressMax = runtimes.size.coerceAtLeast(1),
            reviewProgressText = resourceProvider.getString(
                R.string.practice_listening_review_progress_format,
                runtimes.values.count { it.reviewRequired },
                runtimes.size
            ),
            feedbackMessage = resourceProvider.getString(
                if (viewedAnswer) {
                    R.string.practice_listening_feedback_reveal_answer
                } else {
                    R.string.practice_listening_feedback_wrong
                }
            ),
            feedbackTone = ListeningFeedbackTone.ERROR,
            selectedMeaningIndex = selectedIndex,
            bottomActionVisible = false,
            showMeaningQuestion = false,
            showSpellingQuestion = false,
            showStudyState = true,
            showReportState = false,
            studyWord = runtime.word.word,
            studyPhoneticChipText = buildPhoneticChip(runtime.word),
            studyDefinitions = runtime.definitions.take(3).map {
                ListeningStudyDefinitionUi(
                    partOfSpeech = it.partOfSpeech.abbr,
                    meaning = it.meaningChinese
                )
            },
            studyExampleEnglish = runtime.examples.firstOrNull()?.englishSentence.orEmpty(),
            studyExampleChinese = runtime.examples.firstOrNull()?.chineseTranslation.orEmpty(),
            studyReviewStatusText = resourceProvider.getString(
                if (card.queueType == QueueType.REVIEW) {
                    R.string.practice_listening_study_status_review
                } else {
                    R.string.practice_listening_study_status_first_wrong
                },
                runtime.consecutiveCorrect,
                REVIEW_TARGET
            ),
            primaryButtonText = resourceProvider.getString(
                R.string.practice_listening_continue_practice
            ),
            primaryButtonEnabled = true,
            speech = speechCache[runtime.word.id],
            autoPlayRequestId = nextAutoPlayRequestIdIfNeeded(speechCache[runtime.word.id]),
            summary = buildSummary()
        )
        requestSpeech(runtime.word)
    }

    private fun publishReport(emptySummary: String? = null) {
        autoAdvanceJob?.cancel()
        currentScreen = ListeningScreen.REPORT
        currentSpeechWordId = null
        activeCard = null
        val reviewedWords = runtimes.values.filter { it.enteredReview }.map {
            ListeningReportWordUi(
                word = it.word.word,
                progressText = "${it.consecutiveCorrect.coerceAtMost(REVIEW_TARGET)}/$REVIEW_TARGET",
                isCompleted = !it.reviewRequired
            )
        }
        val unfinishedWords = runtimes.values.filter { it.reviewRequired || !it.mastered }.map {
            ListeningReportWordUi(
                word = it.word.word,
                progressText = "${it.consecutiveCorrect.coerceAtMost(REVIEW_TARGET)}/$REVIEW_TARGET",
                isCompleted = false
            )
        }
        val accuracyPercent = if (gradedAttemptCount == 0) {
            0
        } else {
            ((correctAttemptCount.toFloat() / gradedAttemptCount.toFloat()) * 100f).roundToInt()
        }
        _uiState.value = ListeningUiState(
            loading = false,
            hasStarted = true,
            mode = _uiState.value.mode,
            modeTitle = resourceProvider.getString(_uiState.value.mode.titleRes),
            modeDescription = resourceProvider.getString(_uiState.value.mode.descriptionRes),
            headerProgressText = resourceProvider.getString(
                R.string.practice_listening_progress_format,
                runtimes.values.count { it.mastered },
                runtimes.size
            ),
            progressValue = runtimes.values.count { it.mastered },
            progressMax = runtimes.size.coerceAtLeast(1),
            reviewProgressText = resourceProvider.getString(
                R.string.practice_listening_review_progress_format,
                unfinishedWords.size,
                runtimes.size
            ),
            promptText = resourceProvider.getString(R.string.practice_listening_report_title),
            promptHint = resourceProvider.getString(R.string.practice_listening_report_hint),
            showMeaningQuestion = false,
            showSpellingQuestion = false,
            showStudyState = false,
            showReportState = true,
            primaryButtonText = resourceProvider.getString(R.string.practice_completed),
            primaryButtonEnabled = true,
            report = ListeningReportUi(
                accuracyText = resourceProvider.getString(
                    R.string.practice_listening_report_accuracy,
                    accuracyPercent
                ),
                reviewedCountText = reviewedWords.size.toString(),
                skippedCountText = skippedCount.toString(),
                reviewedWords = reviewedWords,
                unfinishedWords = unfinishedWords,
                summaryText = emptySummary ?: resourceProvider.getString(
                    R.string.practice_listening_report_summary,
                    runtimes.values.count { it.mastered },
                    runtimes.size,
                    gradedAttemptCount,
                    correctAttemptCount
                )
            ),
            summary = buildSummary()
        )
    }

    private fun enqueueForReview(wordId: Long, prioritize: Boolean = false) {
        val runtime = runtimes[wordId] ?: return
        runtime.reviewEnqueued = true
        if (reviewQueue.remove(wordId) || prioritize) {
            reviewQueue.addFirst(wordId)
        } else {
            reviewQueue.addLast(wordId)
        }
    }

    private fun requestSpeech(word: Word) {
        val cached = speechCache[word.id]
        if (cached != null) return
        val requestToken = ++speechRequestToken
        viewModelScope.launch {
            val speech = synthesizeSpeech(
                SpeechTask.SynthesizeWord(
                    text = word.word,
                    locale = "en-US"
                )
            ) as? SpeechAudioSuccess
            speechCache[word.id] = speech
            if (speechRequestToken != requestToken || currentSpeechWordId != word.id) return@launch
            _uiState.value = _uiState.value.copy(
                speech = speech,
                autoPlayRequestId = nextAutoPlayRequestIdIfNeeded(speech)
            )
        }
    }

    private fun nextAutoPlayRequestIdIfNeeded(speech: SpeechAudioSuccess?): Int {
        if (speech == null) return autoPlayRequestId
        autoPlayRequestId += 1
        return autoPlayRequestId
    }

    private fun buildSummary(): PracticeSessionSummary {
        return PracticeSessionSummary(
            questionCount = runtimes.size,
            completedCount = runtimes.values.count { it.mastered },
            correctCount = correctAttemptCount,
            submitCount = gradedAttemptCount
        )
    }

    private fun buildPhoneticChip(word: Word): String {
        val phonetic = word.phoneticUS?.takeIf { it.isNotBlank() }
            ?: word.phoneticUK?.takeIf { it.isNotBlank() }
            ?: resourceProvider.getString(R.string.practice_listening_phonetic_empty)
        return resourceProvider.getString(
            R.string.practice_listening_phonetic_chip,
            phonetic
        )
    }

    private fun sanitizeSpellingInput(input: String): String {
        return input.filter { char ->
            char.isLetter() || char == '\'' || char == '-' || char.isWhitespace()
        }.trimStart()
    }

    private fun normalizeAnswer(answer: String): String {
        return answer.trim().lowercase().replace(" ", "")
    }

    private fun initialState(): ListeningUiState {
        val defaultMode = ListeningPracticeMode.RANDOM
        return ListeningUiState(
            mode = defaultMode,
            modeTitle = resourceProvider.getString(defaultMode.titleRes),
            modeDescription = resourceProvider.getString(defaultMode.descriptionRes),
            headerProgressText = resourceProvider.getString(
                R.string.practice_listening_progress_format,
                0,
                0
            ),
            reviewProgressText = resourceProvider.getString(
                R.string.practice_listening_review_progress_format,
                0,
                0
            ),
            promptText = resourceProvider.getString(R.string.practice_listening_select_mode_first),
            promptHint = resourceProvider.getString(R.string.practice_listening_select_mode_hint)
        )
    }

    companion object {
        private const val REVIEW_TARGET = 3
        private const val CORRECT_FEEDBACK_DURATION_MS = 650L
    }
}
