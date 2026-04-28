package com.chen.memorizewords.feature.learning.ui.practice

import androidx.annotation.StringRes
import androidx.lifecycle.viewModelScope
import com.chen.memorizewords.core.common.resource.ResourceProvider
import com.chen.memorizewords.core.ui.vm.BaseViewModel
import com.chen.memorizewords.domain.model.study.progress.word.WordLearningState
import com.chen.memorizewords.domain.model.words.word.PronunciationType
import com.chen.memorizewords.domain.model.words.word.Word
import com.chen.memorizewords.domain.model.words.word.WordDefinitions
import com.chen.memorizewords.domain.model.words.word.WordExample
import com.chen.memorizewords.domain.practice.PracticeWordProvider
import com.chen.memorizewords.domain.query.word.WordReadFacade
import com.chen.memorizewords.domain.repository.practice.ListeningPracticePreferencesRepository
import com.chen.memorizewords.domain.usecase.practice.SynthesizeSpeechUseCase
import com.chen.memorizewords.domain.usecase.word.study.GetWordLearningStatesByBookIdUseCase
import com.chen.memorizewords.feature.learning.R
import com.chen.memorizewords.feature.learning.ui.practice.listening.LISTENING_CORRECT_FEEDBACK_DURATION_MS
import com.chen.memorizewords.feature.learning.ui.practice.listening.LISTENING_MEANING_SELECTION_TRANSITION_DELAY_MS
import com.chen.memorizewords.feature.learning.ui.practice.listening.LISTENING_REVIEW_TARGET
import com.chen.memorizewords.feature.learning.ui.practice.listening.LISTENING_SPEECH_LOCALE_US
import com.chen.memorizewords.feature.learning.ui.practice.listening.LISTENING_WRONG_MEANING_SELECTION_TRANSITION_DELAY_MS
import com.chen.memorizewords.feature.learning.ui.practice.listening.LISTENING_WRONG_SPELLING_FEEDBACK_DURATION_MS
import com.chen.memorizewords.feature.learning.ui.practice.listening.audio.ListeningSentenceSpeechKey
import com.chen.memorizewords.feature.learning.ui.practice.listening.audio.ListeningSpeechController
import com.chen.memorizewords.feature.learning.ui.practice.listening.audio.ListeningWordSpeechKey
import com.chen.memorizewords.feature.learning.ui.practice.listening.ListeningAction
import com.chen.memorizewords.feature.learning.ui.practice.listening.ListeningSessionConfig
import com.chen.memorizewords.feature.learning.ui.practice.listening.ListeningSessionState
import com.chen.memorizewords.feature.learning.ui.practice.listening.engine.ListeningSessionEngine
import com.chen.memorizewords.feature.learning.ui.practice.listening.policy.ListeningQueueType
import com.chen.memorizewords.feature.learning.ui.practice.listening.policy.ReviewQueuePolicy
import com.chen.memorizewords.feature.learning.ui.practice.listening.presentation.ListeningUiMapper
import com.chen.memorizewords.feature.learning.ui.practice.listening.strategy.MeaningListeningQuestionStrategy
import com.chen.memorizewords.feature.learning.ui.practice.listening.strategy.SpellingListeningQuestionStrategy
import com.chen.memorizewords.speech.api.SpeechAudioSuccess
import dagger.hilt.android.lifecycle.HiltViewModel
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
    SPELLING(
        titleRes = R.string.practice_listening_mode_spelling,
        descriptionRes = R.string.practice_listening_mode_spelling_desc
    ),
    MEANING(
        titleRes = R.string.practice_listening_mode_meaning,
        descriptionRes = R.string.practice_listening_mode_meaning_desc
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

enum class ListeningFeedbackTone {
    NONE,
    INFO,
    SUCCESS,
    ERROR
}

enum class ListeningFooterMode {
    NONE,
    PRACTICE_ACTIONS,
    PRIMARY_ACTION
}

enum class ListeningMeaningOptionFeedback {
    DEFAULT,
    CORRECT,
    WRONG
}

enum class ListeningSpellingSlotFeedback {
    DEFAULT,
    WRONG
}

data class ListeningMeaningOptionUi(
    val id: Long,
    val partOfSpeech: String,
    val content: String,
    val isCorrect: Boolean
)

data class ListeningSpellingSlotUi(
    val character: String = "",
    val sourceLetterId: Long? = null,
    val feedback: ListeningSpellingSlotFeedback = ListeningSpellingSlotFeedback.DEFAULT
)

data class ListeningSpellingLetterUi(
    val id: Long,
    val character: String,
    val isUsed: Boolean = false
)

data class ListeningStudyDefinitionUi(
    val partOfSpeech: String,
    val meaning: String
)

data class ListeningStudyExampleUi(
    val englishText: String,
    val chineseText: String
)

data class ListeningReportWordUi(
    val wordId: Long = 0L,
    val word: String,
    val meaningText: String = "",
    val speechLocale: String = "en-US",
    val progressText: String = "",
    val isCompleted: Boolean = false
)

data class ListeningReportUi(
    val accuracyText: String = "0%",
    val accuracyPercent: Int = 0,
    val reviewedCountText: String = "0",
    val skippedCountText: String = "0",
    val summaryPrimaryText: String = "",
    val summarySecondaryText: String = "",
    val focusedReviewWords: List<ListeningReportWordUi> = emptyList(),
    val allWords: List<ListeningReportWordUi> = emptyList(),
    val reviewedWords: List<ListeningReportWordUi> = emptyList(),
    val unfinishedWords: List<ListeningReportWordUi> = emptyList(),
    val summaryText: String = ""
)

internal fun listeningModeDisplayName(mode: ListeningPracticeMode): String {
    return when (mode) {
        ListeningPracticeMode.SPELLING -> "\u8fa8\u97f3\u62fc\u5199"
        ListeningPracticeMode.MEANING -> "\u8fa8\u97f3\u9009\u4e49"
    }
}

internal fun resolveListeningPracticeMode(modeName: String?): ListeningPracticeMode {
    return runCatching {
        ListeningPracticeMode.valueOf(modeName.orEmpty())
    }.getOrDefault(ListeningPracticeMode.MEANING)
}

internal fun buildListeningScreenTitle(progressText: String): String {
    return "\u542c\u529b\u6d4b\u8bd5\uff08$progressText\uff09"
}

@HiltViewModel
class ListeningPracticeViewModel @Inject constructor(
    private val resourceProvider: ResourceProvider,
    private val wordReadFacade: WordReadFacade,
    private val synthesizeSpeech: SynthesizeSpeechUseCase,
    private val wordProvider: PracticeWordProvider,
    private val getWordLearningStatesByBookId: GetWordLearningStatesByBookIdUseCase,
    private val listeningPracticePreferencesRepository: ListeningPracticePreferencesRepository
) : BaseViewModel() {

    data class ListeningUiState(
        val loading: Boolean = false,
        val hasStarted: Boolean = false,
        val mode: ListeningPracticeMode = ListeningPracticeMode.MEANING,
        val modeTitle: String = "",
        val modeDescription: String = "",
        val headerProgressText: String = "0/0",
        val progressValue: Int = 0,
        val progressMax: Int = 1,
        val reviewProgressText: String = "",
        val reviewIndicatorText: String = "",
        val screenTitleText: String = "",
        val modeBadgeText: String = "",
        val instructionPrimaryText: String = "",
        val instructionSecondaryText: String = "",
        val footerMode: ListeningFooterMode = ListeningFooterMode.NONE,
        val promptText: String = "",
        val promptHint: String = "",
        val phoneticChipText: String = "",
        val feedbackMessage: String = "",
        val feedbackTone: ListeningFeedbackTone = ListeningFeedbackTone.NONE,
        val questionType: ListeningQuestionType = ListeningQuestionType.MEANING,
        val meaningOptions: List<ListeningMeaningOptionUi> = emptyList(),
        val selectedMeaningIndex: Int? = null,
        val meaningOptionFeedback: List<ListeningMeaningOptionFeedback> = emptyList(),
        val wrongMeaningShakeIndex: Int? = null,
        val wrongMeaningShakeRequestId: Int = 0,
        val spellingInput: String = "",
        val spellingSlots: List<ListeningSpellingSlotUi> = emptyList(),
        val spellingLetterPool: List<ListeningSpellingLetterUi> = emptyList(),
        val wrongSpellingShakeIndexes: List<Int> = emptyList(),
        val wrongSpellingShakeRequestId: Int = 0,
        val showSpellingAnswerFeedback: Boolean = false,
        val spellingAnswerFeedbackText: String = "",
        val spellingSubmitEnabled: Boolean = false,
        val isMeaningTransitionPending: Boolean = false,
        val showMeaningQuestion: Boolean = false,
        val showSpellingQuestion: Boolean = false,
        val showStudyState: Boolean = false,
        val showReportState: Boolean = false,
        val studyWord: String = "",
        val studyPronunciationType: PronunciationType = PronunciationType.US,
        val studyPhoneticLocaleLabel: String = "",
        val studyPhoneticChipText: String = "",
        val studySpeechLocale: String = "en-US",
        val studyPhoneticToggleEnabled: Boolean = false,
        val studyDefinitions: List<ListeningStudyDefinitionUi> = emptyList(),
        val studyExamples: List<ListeningStudyExampleUi> = emptyList(),
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
        val queueType: ListeningQueueType,
        val questionType: ListeningQuestionType
    )

    private val _uiState = MutableStateFlow(initialState())
    val uiState: StateFlow<ListeningUiState> = _uiState.asStateFlow()

    private val _sessionWordIds = MutableStateFlow<List<Long>>(emptyList())
    val sessionWordIds: StateFlow<List<Long>> = _sessionWordIds.asStateFlow()

    private val runtimes = linkedMapOf<Long, WordRuntime>()
    private val reviewQueuePolicy = ReviewQueuePolicy()
    private val meaningStrategy = MeaningListeningQuestionStrategy()
    private val spellingStrategy = SpellingListeningQuestionStrategy()
    private val sessionEngine = ListeningSessionEngine()
    private val uiMapper = ListeningUiMapper(resourceProvider)
    private val speechController = ListeningSpeechController(synthesizeSpeech)

    private var selectedWordIds: LongArray? = null
    private var selectedRandomCount: Int = 20
    private var loadKey: String? = null
    private var sessionState: ListeningSessionState = ListeningSessionState()
    private var activeCard: ActiveCard? = null
    private var currentScreen: ListeningScreen = ListeningScreen.PRACTICE
    private var currentSpeechCacheKey: ListeningWordSpeechKey? = null
    private var speechRequestToken: Int = 0
    private var autoPlayRequestId: Int = 0
    private var correctAttemptCount: Int = 0
    private var gradedAttemptCount: Int = 0
    private var skippedCount: Int = 0
    private var autoAdvanceJob: Job? = null
    private var wrongMeaningShakeRequestId: Int = 0
    private var wrongSpellingShakeRequestId: Int = 0
    private var nextSpellingLetterId: Long = 1L
    private var spellingShuffleSeedCounter: Long = 0L

    fun loadWithSelection(selectedIds: LongArray?, randomCount: Int) {
        selectedWordIds = selectedIds?.clone()
        selectedRandomCount = randomCount
    }

    suspend fun startSavedSession() {
        val savedModeName = listeningPracticePreferencesRepository.getLastListeningPracticeModeName()
        startSession(resolveListeningPracticeMode(savedModeName))
    }

    fun onModeChanged(mode: ListeningPracticeMode) {
        viewModelScope.launch {
            listeningPracticePreferencesRepository.saveLastListeningPracticeModeName(mode.name)
            sessionState = sessionEngine.reduce(
                state = sessionState,
                action = ListeningAction.ChangeMode(mode)
            ).state
            startSession(mode)
        }
    }

    suspend fun consumeModeSwitchHint(): Boolean {
        if (listeningPracticePreferencesRepository.hasShownModeSwitchHint()) return false
        listeningPracticePreferencesRepository.markModeSwitchHintShown()
        return true
    }

    fun requestExitPracticeConfirm() {
        val fallbackProgressText = resourceProvider.getString(
            R.string.practice_listening_progress_format,
            0,
            0
        )
        showConfirmDialog(
            action = ACTION_EXIT_LISTENING_PRACTICE,
            title = _uiState.value.screenTitleText.ifBlank {
                buildListeningScreenTitle(fallbackProgressText)
            },
            message = resourceProvider.getString(R.string.practice_listening_exit_confirm_message),
            confirmText = resourceProvider.getString(R.string.practice_listening_exit_confirm_action),
            cancelText = resourceProvider.getString(android.R.string.cancel)
        )
    }

    fun startSession(mode: ListeningPracticeMode) {
        val selectedIds = selectedWordIds?.clone()
        val randomCount = selectedRandomCount
        val config = ListeningSessionConfig(
            selectedIds = selectedIds,
            randomCount = randomCount,
            mode = mode
        )
        val newLoadKey = config.key
        if (loadKey == newLoadKey && _uiState.value.hasStarted) return
        sessionState = sessionEngine.reduce(
            state = sessionState,
            action = ListeningAction.StartSession(config)
        ).state
        loadKey = newLoadKey
        autoAdvanceJob?.cancel()
        viewModelScope.launch {
            _uiState.value = initialState().copy(
                loading = true,
                hasStarted = true,
                mode = mode,
                modeTitle = listeningModeDisplayName(mode),
                modeDescription = resourceProvider.getString(mode.descriptionRes),
                headerProgressText = resourceProvider.getString(
                    R.string.practice_listening_progress_format,
                    0,
                    0
                ),
                screenTitleText = buildListeningScreenTitle(
                    resourceProvider.getString(
                        R.string.practice_listening_progress_format,
                        0,
                        0
                    )
                ),
                modeBadgeText = resourceProvider.getString(
                    R.string.practice_listening_mode_badge,
                    listeningModeDisplayName(mode)
                ),
                instructionPrimaryText = resourceProvider.getString(R.string.practice_listening_loading),
                promptText = resourceProvider.getString(R.string.practice_listening_loading)
            )

            val words = wordProvider.loadWords(
                selectedIds = selectedIds,
                randomCount = randomCount,
                defaultLimit = 20
            )
            val statesByWordId = loadLearningStatesByWordId()
            val orderedWords = orderWordsForMode(words)

            runtimes.clear()
            speechController.clear()
            activeCard = null
            currentSpeechCacheKey = null
            correctAttemptCount = 0
            gradedAttemptCount = 0
            skippedCount = 0
            autoPlayRequestId = 0
            nextSpellingLetterId = 1L
            spellingShuffleSeedCounter = 0L

            val runtimeList = buildRuntimeList(orderedWords, statesByWordId)
            runtimeList.forEach { runtime ->
                runtimes[runtime.word.id] = runtime
            }
            reviewQueuePolicy.reset(runtimeList.map { it.word.id })
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
        if (state.isMeaningTransitionPending) return
        if (state.selectedMeaningIndex != null) return
        val runtime = runtimes[card.wordId] ?: return
        runtime.meaningOptions.getOrNull(index) ?: return
        sessionState = sessionEngine.reduce(
            state = sessionState,
            action = ListeningAction.SelectMeaning(index)
        ).state
        if (meaningStrategy.isCorrect(runtime.meaningOptions, index)) {
            handleCorrectAnswer(
                runtime = runtime,
                card = card,
                selectedIndex = index,
                transitionDelayMs = LISTENING_MEANING_SELECTION_TRANSITION_DELAY_MS,
                markMeaningTransitionPending = true
            )
        } else {
            handleWrongMeaningAnswer(runtime, card, selectedIndex = index)
        }
    }

    fun onSpellingLetterSelected(letterId: Long) {
        if (currentScreen != ListeningScreen.PRACTICE) return
        if (activeCard?.questionType != ListeningQuestionType.SPELLING) return
        val current = _uiState.value
        val change = spellingStrategy.selectLetter(
            slots = current.spellingSlots,
            letterPool = current.spellingLetterPool,
            letterId = letterId
        ) ?: return
        updateSpellingDraft(change.slots, change.letterPool)
    }

    fun onSpellingDeleteLast() {
        if (currentScreen != ListeningScreen.PRACTICE) return
        if (activeCard?.questionType != ListeningQuestionType.SPELLING) return
        val current = _uiState.value
        val change = spellingStrategy.deleteLast(
            slots = current.spellingSlots,
            letterPool = current.spellingLetterPool
        ) ?: return
        updateSpellingDraft(change.slots, change.letterPool)
    }

    fun submitSpellingAnswer() {
        val card = activeCard ?: return
        if (card.questionType != ListeningQuestionType.SPELLING) return
        if (currentScreen != ListeningScreen.PRACTICE) return
        val runtime = runtimes[card.wordId] ?: return
        val input = _uiState.value.spellingInput
        if (input.isBlank()) return
        if (spellingStrategy.isCorrect(input, runtime.word.word)) {
            handleCorrectAnswer(runtime, card)
        } else {
            handleWrongAnswer(runtime, card)
        }
    }

    fun onRevealAnswer() {
        val card = activeCard ?: return
        val runtime = runtimes[card.wordId] ?: return
        if (currentScreen != ListeningScreen.PRACTICE) return
        if (_uiState.value.isMeaningTransitionPending) return
        handleWrongAnswer(runtime, card, viewedAnswer = true)
    }

    fun onSkipQuestion() {
        val runtime = activeCard?.let { runtimes[it.wordId] } ?: return
        if (currentScreen != ListeningScreen.PRACTICE) return
        if (_uiState.value.isMeaningTransitionPending) return
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

    fun onStudyPhoneticToggle() {
        if (currentScreen != ListeningScreen.STUDY) return
        val card = activeCard ?: return
        val runtime = runtimes[card.wordId] ?: return
        val current = _uiState.value
        if (!current.studyPhoneticToggleEnabled) return
        val toggledType = when (current.studyPronunciationType) {
            PronunciationType.US -> PronunciationType.UK
            PronunciationType.UK -> PronunciationType.US
        }
        val nextPhoneticUi = uiMapper.studyPronunciation(runtime.word, toggledType)
        if (nextPhoneticUi.pronunciationType == current.studyPronunciationType) return
        val speechKey = ListeningWordSpeechKey(runtime.word.id, nextPhoneticUi.speechLocale)
        currentSpeechCacheKey = speechKey
        _uiState.value = current.copy(
            studyPronunciationType = nextPhoneticUi.pronunciationType,
            studyPhoneticLocaleLabel = nextPhoneticUi.localeLabel,
            studyPhoneticChipText = nextPhoneticUi.phoneticText,
            studySpeechLocale = nextPhoneticUi.speechLocale,
            studyPhoneticToggleEnabled = nextPhoneticUi.toggleEnabled,
            speech = speechController.cachedWordSpeech(speechKey)
        )
        requestSpeech(runtime.word, shouldAutoPlay = false)
    }

    suspend fun ensureCurrentSpeech(): SpeechAudioSuccess? {
        val wordId = when (currentScreen) {
            ListeningScreen.PRACTICE -> activeCard?.wordId
            ListeningScreen.STUDY -> activeCard?.wordId
            ListeningScreen.REPORT -> null
        } ?: return null
        val locale = resolveCurrentSpeechLocale()
        val cacheKey = ListeningWordSpeechKey(wordId, locale)
        currentSpeechCacheKey = cacheKey
        val cached = speechController.cachedWordSpeech(cacheKey)
        if (cached != null) return cached
        val runtime = runtimes[wordId] ?: return null
        val speech = speechController.resolveWordSpeech(cacheKey, runtime.word.word)
        if (currentSpeechCacheKey == cacheKey && speech != null) {
            _uiState.value = _uiState.value.copy(speech = speech)
        }
        return speech
    }

    suspend fun ensureReportWordSpeech(wordId: Long, locale: String): SpeechAudioSuccess? {
        val cacheKey = ListeningWordSpeechKey(wordId, locale)
        currentSpeechCacheKey = cacheKey
        val cached = speechController.cachedWordSpeech(cacheKey)
        if (cached != null) return cached
        val runtime = runtimes[wordId] ?: return null
        return speechController.resolveWordSpeech(cacheKey, runtime.word.word)
    }

    suspend fun ensureStudyExampleSpeech(index: Int): SpeechAudioSuccess? {
        if (currentScreen != ListeningScreen.STUDY) return null
        val wordId = activeCard?.wordId ?: return null
        val example = _uiState.value.studyExamples.getOrNull(index) ?: return null
        val cacheKey = ListeningSentenceSpeechKey(
            wordId = wordId,
            text = example.englishText,
            locale = LISTENING_SPEECH_LOCALE_US
        )
        return speechController.resolveSentenceSpeech(cacheKey)
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

    private fun orderWordsForMode(words: List<Word>): List<Word> {
        return words
    }

    private fun moveToNextQuestion(
        feedbackMessage: String = "",
        feedbackTone: ListeningFeedbackTone = ListeningFeedbackTone.NONE
    ) {
        autoAdvanceJob?.cancel()
        val nextSelection = reviewQueuePolicy.selectNext()
        if (nextSelection == null) {
            publishReport()
            return
        }
        val nextWordId = nextSelection.wordId
        val runtime = runtimes[nextWordId] ?: run {
            publishReport()
            return
        }
        val queueType = nextSelection.queueType
        val questionType = resolveQuestionType(runtime)
        activeCard = ActiveCard(
            wordId = nextWordId,
            queueType = queueType,
            questionType = questionType
        )
        sessionState = sessionState.copy(
            activeWordId = nextWordId,
            activeQuestionType = questionType,
            isTransitionPending = false
        )
        if (queueType == ListeningQueueType.REVIEW) {
            runtime.reviewEnqueued = false
        }
        currentScreen = ListeningScreen.PRACTICE
        renderPracticeState(runtime, questionType, feedbackMessage, feedbackTone)
    }

    private fun resolveQuestionType(runtime: WordRuntime): ListeningQuestionType {
        val canMeaning = runtime.meaningOptions.isNotEmpty()
        val canSpelling = runtime.word.word.isNotBlank()
        return when (_uiState.value.mode) {
            ListeningPracticeMode.MEANING -> {
                if (canMeaning) ListeningQuestionType.MEANING else ListeningQuestionType.SPELLING
            }

            ListeningPracticeMode.SPELLING -> ListeningQuestionType.SPELLING
        }
    }

    private fun renderPracticeState(
        runtime: WordRuntime,
        questionType: ListeningQuestionType,
        feedbackMessage: String,
        feedbackTone: ListeningFeedbackTone
    ) {
        val practiceSpeechLocale = resolvePracticeSpeechLocale()
        val practiceSpeechKey = ListeningWordSpeechKey(runtime.word.id, practiceSpeechLocale)
        currentSpeechCacheKey = practiceSpeechKey
        val progressText = resourceProvider.getString(
            R.string.practice_listening_progress_format,
            runtimes.values.count { it.mastered },
            runtimes.size
        )
        val instructionPrimary = if (questionType == ListeningQuestionType.MEANING) {
            resourceProvider.getString(R.string.practice_listening_prompt_meaning)
        } else {
            resourceProvider.getString(R.string.practice_listening_prompt_spelling)
        }
        val instructionSecondary = if (questionType == ListeningQuestionType.SPELLING) {
            resourceProvider.getString(R.string.practice_listening_prompt_spelling_hint)
        } else {
            ""
        }
        val spellingQuestionUi = if (questionType == ListeningQuestionType.SPELLING) {
            spellingStrategy.buildQuestion(
                word = runtime.word,
                shuffleSeedCounter = spellingShuffleSeedCounter++,
                firstLetterId = nextSpellingLetterId
            ).also { result ->
                nextSpellingLetterId = result.nextLetterId
            }
        } else {
            null
        }
        _uiState.value = ListeningUiState(
            loading = false,
            hasStarted = true,
            mode = _uiState.value.mode,
            modeTitle = listeningModeDisplayName(_uiState.value.mode),
            modeDescription = resourceProvider.getString(_uiState.value.mode.descriptionRes),
            headerProgressText = progressText,
            progressValue = runtimes.values.count { it.mastered },
            progressMax = runtimes.size.coerceAtLeast(1),
            reviewProgressText = resourceProvider.getString(
                R.string.practice_listening_review_progress_format,
                runtimes.values.count { it.reviewRequired },
                runtimes.size
            ),
            screenTitleText = buildListeningScreenTitle(progressText),
            modeBadgeText = resourceProvider.getString(
                R.string.practice_listening_mode_badge,
                listeningModeDisplayName(_uiState.value.mode)
            ),
            reviewIndicatorText = if (runtime.reviewRequired) {
                resourceProvider.getString(
                    R.string.practice_listening_review_badge,
                    runtime.consecutiveCorrect,
                    LISTENING_REVIEW_TARGET
                )
            } else {
                resourceProvider.getString(R.string.practice_listening_new_word_badge)
            },
            instructionPrimaryText = instructionPrimary,
            instructionSecondaryText = instructionSecondary,
            footerMode = ListeningFooterMode.PRACTICE_ACTIONS,
            promptText = instructionPrimary,
            promptHint = instructionSecondary,
            phoneticChipText = uiMapper.phoneticChip(runtime.word),
            feedbackMessage = feedbackMessage,
            feedbackTone = feedbackTone,
            questionType = questionType,
            meaningOptions = runtime.meaningOptions,
            selectedMeaningIndex = null,
            meaningOptionFeedback = if (questionType == ListeningQuestionType.MEANING) {
                meaningStrategy.defaultFeedback(runtime.meaningOptions)
            } else {
                emptyList()
            },
            wrongMeaningShakeIndex = null,
            wrongMeaningShakeRequestId = 0,
            spellingInput = "",
            spellingSlots = spellingQuestionUi?.slots.orEmpty(),
            spellingLetterPool = spellingQuestionUi?.letterPool.orEmpty(),
            wrongSpellingShakeIndexes = emptyList(),
            wrongSpellingShakeRequestId = 0,
            showSpellingAnswerFeedback = false,
            spellingAnswerFeedbackText = "",
            spellingSubmitEnabled = false,
            isMeaningTransitionPending = false,
            showMeaningQuestion = questionType == ListeningQuestionType.MEANING,
            showSpellingQuestion = questionType == ListeningQuestionType.SPELLING,
            showStudyState = false,
            showReportState = false,
            studyPronunciationType = PronunciationType.US,
            studyPhoneticLocaleLabel = "",
            studyPhoneticChipText = "",
            studySpeechLocale = resolvePracticeSpeechLocale(),
            studyPhoneticToggleEnabled = false,
            studyExamples = emptyList(),
            primaryButtonText = "",
            primaryButtonEnabled = false,
            speech = speechController.cachedWordSpeech(practiceSpeechKey),
            autoPlayRequestId = nextAutoPlayRequestIdIfNeeded(
                speechController.cachedWordSpeech(practiceSpeechKey)
            ),
            summary = buildSummary()
        )
        requestSpeech(runtime.word)
    }

    private fun handleCorrectAnswer(
        runtime: WordRuntime,
        card: ActiveCard,
        selectedIndex: Int? = null,
        transitionDelayMs: Long = LISTENING_CORRECT_FEEDBACK_DURATION_MS,
        markMeaningTransitionPending: Boolean = false
    ) {
        autoAdvanceJob?.cancel()
        gradedAttemptCount += 1
        correctAttemptCount += 1
        val masteredNow = if (card.queueType == ListeningQueueType.REVIEW || runtime.reviewRequired) {
            runtime.consecutiveCorrect += 1
            if (reviewQueuePolicy.isReviewGoalMet(runtime.consecutiveCorrect)) {
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
        val progressText = resourceProvider.getString(
            R.string.practice_listening_progress_format,
            runtimes.values.count { it.mastered },
            runtimes.size
        )
        _uiState.value = _uiState.value.copy(
            headerProgressText = progressText,
            progressValue = runtimes.values.count { it.mastered },
            progressMax = runtimes.size.coerceAtLeast(1),
            reviewProgressText = resourceProvider.getString(
                R.string.practice_listening_review_progress_format,
                runtimes.values.count { it.reviewRequired },
                runtimes.size
            ),
            screenTitleText = buildListeningScreenTitle(progressText),
            selectedMeaningIndex = selectedIndex,
            meaningOptionFeedback = meaningStrategy.feedback(
                options = runtime.meaningOptions,
                correctIndex = selectedIndex
            ),
            wrongMeaningShakeIndex = null,
            wrongMeaningShakeRequestId = 0,
            isMeaningTransitionPending = markMeaningTransitionPending,
            feedbackMessage = if (masteredNow) {
                resourceProvider.getString(R.string.practice_listening_feedback_correct)
            } else {
                resourceProvider.getString(
                    R.string.practice_listening_feedback_review_progress,
                    runtime.consecutiveCorrect,
                    LISTENING_REVIEW_TARGET
                )
            },
            feedbackTone = ListeningFeedbackTone.SUCCESS,
            summary = buildSummary()
        )
        autoAdvanceJob = viewModelScope.launch {
            delay(transitionDelayMs)
            if (activeCard != card || currentScreen != ListeningScreen.PRACTICE) {
                return@launch
            }
            moveToNextQuestion()
        }
    }

    private fun handleWrongMeaningAnswer(
        runtime: WordRuntime,
        card: ActiveCard,
        selectedIndex: Int
    ) {
        autoAdvanceJob?.cancel()
        applyWrongAnswerOutcome(runtime)
        activeCard = card
        currentScreen = ListeningScreen.PRACTICE
        currentSpeechCacheKey = ListeningWordSpeechKey(runtime.word.id, resolvePracticeSpeechLocale())
        val progressText = resourceProvider.getString(
            R.string.practice_listening_progress_format,
            runtimes.values.count { it.mastered },
            runtimes.size
        )
        _uiState.value = _uiState.value.copy(
            headerProgressText = progressText,
            progressValue = runtimes.values.count { it.mastered },
            progressMax = runtimes.size.coerceAtLeast(1),
            reviewProgressText = resourceProvider.getString(
                R.string.practice_listening_review_progress_format,
                runtimes.values.count { it.reviewRequired },
                runtimes.size
            ),
            screenTitleText = buildListeningScreenTitle(progressText),
            feedbackMessage = resourceProvider.getString(R.string.practice_listening_feedback_wrong),
            feedbackTone = ListeningFeedbackTone.ERROR,
            selectedMeaningIndex = selectedIndex,
            meaningOptionFeedback = meaningStrategy.feedback(
                options = runtime.meaningOptions,
                correctIndex = runtime.meaningOptions.indexOfFirst { it.isCorrect },
                wrongIndex = selectedIndex
            ),
            wrongMeaningShakeIndex = selectedIndex,
            wrongMeaningShakeRequestId = nextWrongMeaningShakeRequestId(),
            isMeaningTransitionPending = true,
            summary = buildSummary()
        )
        autoAdvanceJob = viewModelScope.launch {
            delay(LISTENING_WRONG_MEANING_SELECTION_TRANSITION_DELAY_MS)
            if (activeCard != card || currentScreen != ListeningScreen.PRACTICE) {
                return@launch
            }
            showStudyState(runtime, card, selectedIndex = selectedIndex)
        }
    }

    private fun handleWrongAnswer(
        runtime: WordRuntime,
        card: ActiveCard,
        selectedIndex: Int? = null,
        viewedAnswer: Boolean = false
    ) {
        autoAdvanceJob?.cancel()
        applyWrongAnswerOutcome(runtime)
        activeCard = card
        if (card.questionType == ListeningQuestionType.SPELLING) {
            currentScreen = ListeningScreen.PRACTICE
            val currentSlots = _uiState.value.spellingSlots
            val wrongSlots = if (viewedAnswer) {
                spellingStrategy.resetFeedback(currentSlots)
            } else {
                spellingStrategy.wrongSlots(currentSlots, runtime.word.word)
            }
            val wrongIndexes = spellingStrategy.wrongIndexes(wrongSlots)
            _uiState.value = _uiState.value.copy(
                feedbackMessage = if (viewedAnswer) {
                    resourceProvider.getString(R.string.practice_listening_feedback_reveal_answer)
                } else {
                    resourceProvider.getString(R.string.practice_listening_feedback_wrong)
                },
                feedbackTone = ListeningFeedbackTone.ERROR,
                spellingSlots = wrongSlots,
                wrongSpellingShakeIndexes = wrongIndexes,
                wrongSpellingShakeRequestId = if (wrongIndexes.isNotEmpty() && !viewedAnswer) {
                    nextWrongSpellingShakeRequestId()
                } else {
                    0
                },
                showSpellingAnswerFeedback = true,
                spellingAnswerFeedbackText = resourceProvider.getString(
                    R.string.practice_listening_spelling_correct_answer,
                    runtime.word.word
                ),
                summary = buildSummary()
            )
            autoAdvanceJob = viewModelScope.launch {
                delay(LISTENING_WRONG_SPELLING_FEEDBACK_DURATION_MS)
                if (activeCard != card || currentScreen != ListeningScreen.PRACTICE) {
                    return@launch
                }
                showStudyState(runtime, card, selectedIndex = selectedIndex, viewedAnswer = viewedAnswer)
            }
            return
        }
        showStudyState(runtime, card, selectedIndex = selectedIndex, viewedAnswer = viewedAnswer)
    }

    private fun applyWrongAnswerOutcome(runtime: WordRuntime) {
        gradedAttemptCount += 1
        runtime.wrongCount += 1
        runtime.enteredReview = true
        runtime.reviewRequired = true
        runtime.mastered = false
        runtime.consecutiveCorrect = 0
        enqueueForReview(runtime.word.id, prioritize = runtime.wrongCount >= 2)
    }

    private fun showStudyState(
        runtime: WordRuntime,
        card: ActiveCard,
        selectedIndex: Int? = null,
        viewedAnswer: Boolean = false
    ) {
        currentScreen = ListeningScreen.STUDY
        val studyPhoneticUi = uiMapper.studyPronunciation(runtime.word)
        val studySpeechKey = ListeningWordSpeechKey(runtime.word.id, studyPhoneticUi.speechLocale)
        currentSpeechCacheKey = studySpeechKey
        val progressText = resourceProvider.getString(
            R.string.practice_listening_progress_format,
            runtimes.values.count { it.mastered },
            runtimes.size
        )
        _uiState.value = _uiState.value.copy(
            headerProgressText = progressText,
            progressValue = runtimes.values.count { it.mastered },
            progressMax = runtimes.size.coerceAtLeast(1),
            reviewProgressText = resourceProvider.getString(
                R.string.practice_listening_review_progress_format,
                runtimes.values.count { it.reviewRequired },
                runtimes.size
            ),
            screenTitleText = buildListeningScreenTitle(progressText),
            feedbackMessage = resourceProvider.getString(
                if (viewedAnswer) {
                    R.string.practice_listening_feedback_reveal_answer
                } else {
                    R.string.practice_listening_feedback_wrong
                }
            ),
            feedbackTone = ListeningFeedbackTone.ERROR,
            selectedMeaningIndex = selectedIndex,
            meaningOptionFeedback = emptyList(),
            wrongMeaningShakeIndex = null,
            wrongMeaningShakeRequestId = 0,
            isMeaningTransitionPending = false,
            spellingInput = "",
            spellingSlots = emptyList(),
            spellingLetterPool = emptyList(),
            wrongSpellingShakeIndexes = emptyList(),
            wrongSpellingShakeRequestId = 0,
            showSpellingAnswerFeedback = false,
            spellingAnswerFeedbackText = "",
            spellingSubmitEnabled = false,
            footerMode = ListeningFooterMode.PRIMARY_ACTION,
            showMeaningQuestion = false,
            showSpellingQuestion = false,
            showStudyState = true,
            showReportState = false,
            studyWord = runtime.word.word,
            studyPronunciationType = studyPhoneticUi.pronunciationType,
            studyPhoneticLocaleLabel = studyPhoneticUi.localeLabel,
            studyPhoneticChipText = studyPhoneticUi.phoneticText,
            studySpeechLocale = studyPhoneticUi.speechLocale,
            studyPhoneticToggleEnabled = studyPhoneticUi.toggleEnabled,
            studyDefinitions = runtime.definitions.take(3).map {
                ListeningStudyDefinitionUi(
                    partOfSpeech = it.partOfSpeech.abbr,
                    meaning = it.meaningChinese
                )
            },
            studyExamples = runtime.examples
                .take(2)
                .map { example ->
                    ListeningStudyExampleUi(
                        englishText = example.englishSentence,
                        chineseText = example.chineseTranslation.orEmpty()
                    )
                },
            primaryButtonText = resourceProvider.getString(
                R.string.practice_listening_continue_practice
            ),
            primaryButtonEnabled = true,
            speech = speechController.cachedWordSpeech(studySpeechKey),
            autoPlayRequestId = nextAutoPlayRequestIdIfNeeded(
                speechController.cachedWordSpeech(studySpeechKey)
            ),
            summary = buildSummary()
        )
        requestSpeech(runtime.word)
    }

    private fun publishReport(emptySummary: String? = null) {
        autoAdvanceJob?.cancel()
        currentScreen = ListeningScreen.REPORT
        currentSpeechCacheKey = null
        activeCard = null
        sessionState = sessionState.copy(
            activeWordId = null,
            isTransitionPending = false
        )
        val focusedReviewWords = runtimes.values
            .filter { it.enteredReview }
            .map(::buildReportWordUi)
        val allWords = runtimes.values.map(::buildReportWordUi)
        val reviewedWords = focusedReviewWords
        val unfinishedWords = allWords
        val accuracyPercent = if (gradedAttemptCount == 0) {
            0
        } else {
            ((correctAttemptCount.toFloat() / gradedAttemptCount.toFloat()) * 100f).roundToInt()
        }
        val progressText = resourceProvider.getString(
            R.string.practice_listening_progress_format,
            runtimes.values.count { it.mastered },
            runtimes.size
        )
        _uiState.value = ListeningUiState(
            loading = false,
            hasStarted = true,
            mode = _uiState.value.mode,
            modeTitle = listeningModeDisplayName(_uiState.value.mode),
            modeDescription = resourceProvider.getString(_uiState.value.mode.descriptionRes),
            headerProgressText = progressText,
            progressValue = runtimes.values.count { it.mastered },
            progressMax = runtimes.size.coerceAtLeast(1),
            reviewProgressText = resourceProvider.getString(
                R.string.practice_listening_review_progress_format,
                unfinishedWords.size,
                runtimes.size
            ),
            screenTitleText = buildListeningScreenTitle(progressText),
            modeBadgeText = resourceProvider.getString(
                R.string.practice_listening_mode_badge,
                listeningModeDisplayName(_uiState.value.mode)
            ),
            instructionPrimaryText = resourceProvider.getString(
                R.string.practice_listening_report_title
            ),
            instructionSecondaryText = resourceProvider.getString(
                R.string.practice_listening_report_hint
            ),
            footerMode = ListeningFooterMode.PRIMARY_ACTION,
            promptText = resourceProvider.getString(R.string.practice_listening_report_title),
            promptHint = resourceProvider.getString(R.string.practice_listening_report_hint),
            showMeaningQuestion = false,
            showSpellingQuestion = false,
            meaningOptionFeedback = emptyList(),
            wrongMeaningShakeIndex = null,
            wrongMeaningShakeRequestId = 0,
            spellingInput = "",
            spellingSlots = emptyList(),
            spellingLetterPool = emptyList(),
            wrongSpellingShakeIndexes = emptyList(),
            wrongSpellingShakeRequestId = 0,
            showSpellingAnswerFeedback = false,
            spellingAnswerFeedbackText = "",
            spellingSubmitEnabled = false,
            showStudyState = false,
            showReportState = true,
            studyPronunciationType = PronunciationType.US,
            studyPhoneticLocaleLabel = "",
            studyPhoneticChipText = "",
            studySpeechLocale = resolvePracticeSpeechLocale(),
            studyPhoneticToggleEnabled = false,
            studyExamples = emptyList(),
            primaryButtonText = resourceProvider.getString(
                R.string.practice_listening_report_complete
            ),
            primaryButtonEnabled = true,
            report = ListeningReportUi(
                accuracyText = resourceProvider.getString(
                    R.string.practice_listening_report_accuracy_value,
                    accuracyPercent
                ),
                accuracyPercent = accuracyPercent,
                reviewedCountText = reviewedWords.size.toString(),
                skippedCountText = skippedCount.toString(),
                summaryPrimaryText = emptySummary ?: resourceProvider.getString(
                    R.string.practice_listening_report_summary_completed,
                    runtimes.size
                ),
                summarySecondaryText = if (emptySummary == null) {
                    resourceProvider.getString(
                        R.string.practice_listening_report_summary_attempts,
                        gradedAttemptCount,
                        correctAttemptCount
                    )
                } else {
                    ""
                },
                focusedReviewWords = focusedReviewWords,
                allWords = allWords,
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
        reviewQueuePolicy.enqueueReview(wordId, prioritize)
    }

    private fun buildReportWordUi(runtime: WordRuntime): ListeningReportWordUi {
        return ListeningReportWordUi(
            wordId = runtime.word.id,
            word = runtime.word.word,
            meaningText = runtime.definitions.firstOrNull()?.meaningChinese
                ?.takeIf { it.isNotBlank() }
                ?: resourceProvider.getString(R.string.learning_share_empty_definitions),
            speechLocale = uiMapper.resolveReportSpeechLocale(runtime.word),
            progressText = "${runtime.consecutiveCorrect.coerceAtMost(LISTENING_REVIEW_TARGET)}/$LISTENING_REVIEW_TARGET",
            isCompleted = !runtime.reviewRequired
        )
    }

    private fun requestSpeech(word: Word, shouldAutoPlay: Boolean = true) {
        val locale = resolveCurrentSpeechLocale()
        val cacheKey = ListeningWordSpeechKey(word.id, locale)
        currentSpeechCacheKey = cacheKey
        val cached = speechController.cachedWordSpeech(cacheKey)
        if (cached != null) return
        val requestToken = ++speechRequestToken
        viewModelScope.launch {
            val speech = speechController.resolveWordSpeech(cacheKey, word.word)
            if (speechRequestToken != requestToken || currentSpeechCacheKey != cacheKey) {
                return@launch
            }
            _uiState.value = _uiState.value.copy(
                speech = speech,
                autoPlayRequestId = if (shouldAutoPlay) {
                    nextAutoPlayRequestIdIfNeeded(speech)
                } else {
                    autoPlayRequestId
                }
            )
        }
    }

    private fun nextAutoPlayRequestIdIfNeeded(speech: SpeechAudioSuccess?): Int {
        if (speech == null) return autoPlayRequestId
        autoPlayRequestId += 1
        return autoPlayRequestId
    }

    private fun nextWrongMeaningShakeRequestId(): Int {
        wrongMeaningShakeRequestId += 1
        return wrongMeaningShakeRequestId
    }

    private fun nextWrongSpellingShakeRequestId(): Int {
        wrongSpellingShakeRequestId += 1
        return wrongSpellingShakeRequestId
    }

    private fun updateSpellingDraft(
        slots: List<ListeningSpellingSlotUi>,
        letterPool: List<ListeningSpellingLetterUi>
    ) {
        val resetSlots = spellingStrategy.resetFeedback(slots)
        val input = spellingStrategy.input(resetSlots)
        _uiState.value = _uiState.value.copy(
            spellingInput = input,
            spellingSlots = resetSlots,
            spellingLetterPool = letterPool,
            wrongSpellingShakeIndexes = emptyList(),
            wrongSpellingShakeRequestId = 0,
            showSpellingAnswerFeedback = false,
            spellingAnswerFeedbackText = "",
            spellingSubmitEnabled = input.isNotBlank(),
            feedbackMessage = ""
        )
    }

    private fun buildSummary(): PracticeSessionSummary {
        return PracticeSessionSummary(
            questionCount = runtimes.size,
            completedCount = runtimes.values.count { it.mastered },
            correctCount = correctAttemptCount,
            submitCount = gradedAttemptCount
        )
    }

    private fun resolvePracticeSpeechLocale(): String = LISTENING_SPEECH_LOCALE_US

    private fun resolveCurrentSpeechLocale(): String {
        return if (currentScreen == ListeningScreen.STUDY) {
            _uiState.value.studySpeechLocale.ifBlank { LISTENING_SPEECH_LOCALE_US }
        } else {
            resolvePracticeSpeechLocale()
        }
    }

    private fun initialState(): ListeningUiState {
        val defaultMode = ListeningPracticeMode.MEANING
        val progressText = resourceProvider.getString(
            R.string.practice_listening_progress_format,
            0,
            0
        )
        return ListeningUiState(
            mode = defaultMode,
            modeTitle = listeningModeDisplayName(defaultMode),
            modeDescription = resourceProvider.getString(defaultMode.descriptionRes),
            headerProgressText = progressText,
            reviewProgressText = resourceProvider.getString(
                R.string.practice_listening_review_progress_format,
                0,
                0
            ),
            screenTitleText = buildListeningScreenTitle(progressText),
            modeBadgeText = resourceProvider.getString(
                R.string.practice_listening_mode_badge,
                listeningModeDisplayName(defaultMode)
            ),
            promptText = "",
            promptHint = ""
        )
    }

    companion object {
        const val ACTION_EXIT_LISTENING_PRACTICE = "action_exit_listening_practice"
    }
}
