package com.chen.memorizewords.feature.learning.ui.learning

import androidx.lifecycle.viewModelScope
import com.chen.memorizewords.core.common.resource.ResourceProvider
import com.chen.memorizewords.core.common.session.SessionTimer
import com.chen.memorizewords.core.ui.vm.BaseViewModel
import com.chen.memorizewords.domain.word.query.WordReadFacade
import com.chen.memorizewords.domain.study.orchestrator.learning.LearningSessionTypes
import com.chen.memorizewords.domain.study.model.learning.LearningSessionEngine
import com.chen.memorizewords.domain.study.service.StudyStatsFacade
import com.chen.memorizewords.domain.study.model.record.TodayCheckInEntryState
import com.chen.memorizewords.domain.wordbook.usecase.GetCurrentWordBookUseCase
import com.chen.memorizewords.domain.wordbook.usecase.GetCurrentWordBookSelectionIdUseCase
import com.chen.memorizewords.domain.wordbook.model.learning.LearningTestMode
import com.chen.memorizewords.domain.word.model.word.PronunciationType
import com.chen.memorizewords.domain.word.model.word.Word
import com.chen.memorizewords.domain.study.usecase.word.MarkWordAsLearnedUseCase
import com.chen.memorizewords.domain.study.usecase.word.study.IsFavoriteUseCase
import com.chen.memorizewords.domain.study.usecase.word.study.RecordWordAnswerResultUseCase
import com.chen.memorizewords.domain.study.usecase.word.study.SetWordAsMasteredUseCase
import com.chen.memorizewords.domain.study.usecase.word.study.ToggleFavoriteUseCase
import com.chen.memorizewords.feature.learning.R
import com.chen.memorizewords.feature.learning.ui.done.LearningSessionType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class LearningViewModel @Inject constructor(
    private val recordWordAnswerResult: RecordWordAnswerResultUseCase,
    private val setWordAsMastered: SetWordAsMasteredUseCase,
    private val toggleFavorite: ToggleFavoriteUseCase,
    private val isFavorite: IsFavoriteUseCase,
    private val wordReadFacade: WordReadFacade,
    private val getCurrentWordBookUseCase: GetCurrentWordBookUseCase,
    private val getCurrentWordBookSelectionIdUseCase: GetCurrentWordBookSelectionIdUseCase,
    private val studyStatsFacade: StudyStatsFacade,
    private val markWordAsLearned: MarkWordAsLearnedUseCase,
    private val resourceProvider: ResourceProvider,
) : BaseViewModel() {

    enum class LearningState { TEST, DETAIL }

    companion object {
        private const val LEARN_QUALITY_CORRECT = 4

        internal fun shouldShowWordSurface(
            learningState: LearningState,
            testMode: LearningTestMode
        ): Boolean {
            return learningState == LearningState.DETAIL || testMode == LearningTestMode.MEANING_CHOICE
        }

        internal fun resolveButtonText(
            learningState: LearningState,
            isAnswered: Boolean,
            isCurrentWordCompleted: Boolean,
            isSessionFinished: Boolean
        ): String {
            return when {
                !isAnswered -> ""
                learningState == LearningState.TEST -> "\u7EE7\u7EED"
                isSessionFinished && isCurrentWordCompleted -> "\u5B8C\u6210\u672C\u7EC4"
                isCurrentWordCompleted -> "\u4E0B\u4E00\u8BCD"
                else -> "\u4E0B\u4E00\u4E2A"
            }
        }

        internal fun buildSessionLoadKey(
            sessionTypeValue: Int,
            sessionWordCount: Int,
            initialLearnedCount: Int,
            words: List<Word>
        ): String {
            val wordIds = words.joinToString(separator = ",") { it.id.toString() }
            return "${sessionTypeValue}_${sessionWordCount}_${initialLearnedCount}_$wordIds"
        }

        const val ACTION_EXIT_LEARNING = "action_exit_learning"
    }

    data class LearningUiState(
        val learningState: LearningState = LearningState.TEST,
        val currentWord: Word? = null,
        val learnedWordsCount: Int = 0,
        val totalWordsCount: Int = 0,
        val pronunciationText: String = "",
        val buttonText: String = "",
        val isFavorite: Boolean = false,
        val currentTestMode: LearningTestMode = LearningTestMode.MEANING_CHOICE,
        val showWordSurface: Boolean = true,
        val isWrongTrackWord: Boolean = false,
        val isAnswered: Boolean = false,
        val pronunciationType: PronunciationType = PronunciationType.US,
        val questionToken: Int = 0
    ) {
        val testMode: LearningTestMode get() = currentTestMode
    }

    sealed interface Route {
        data object ToCheckIn : Route

        data class ToWordExamPractice(
            val wordId: Long,
            val wordText: String
        ) : Route

        data class ToLearningDone(
            val wordIds: LongArray,
            val sessionType: Int,
            val sessionWordCount: Int,
            val answeredCount: Int,
            val correctCount: Int,
            val wrongCount: Int,
            val studyDurationMs: Long
        ) : Route
    }

    private val _uiState = MutableStateFlow(LearningUiState())
    val uiState: StateFlow<LearningUiState> = _uiState.asStateFlow()

    private var currentBookId: Long? = null
    var words: List<Word> = emptyList()

    private var prefillLearnedCount: Int = 0
    private var sessionType: Int = 0
    private var sessionWordCount: Int = 0
    private var sessionEngine: LearningSessionEngine? = null
    private var sessionLoadKey: String? = null
    private var wordsById: Map<Long, Word> = emptyMap()

    private var pronunciationType: PronunciationType = PronunciationType.US
    private val sessionTimer = SessionTimer()
    private var trackingEnabled: Boolean = true
    private var sessionFinished: Boolean = false
    private val completionPersistenceGate = LearningCompletionPersistenceGate()

    init {
        viewModelScope.launch {
            currentBookId = getCurrentWordBookSelectionIdUseCase()
                ?: getCurrentWordBookUseCase()?.id
        }
    }

    fun setSessionInfo(type: Int, wordCount: Int) {
        sessionType = type
        sessionWordCount = wordCount.coerceAtLeast(0)
    }

    fun loadDataByIds(
        initialLearnedCount: Int,
        initialWordIds: List<Long>
    ) {
        viewModelScope.launch {
            val initialWordList = withContext(Dispatchers.IO) {
                val words = wordReadFacade.getWordsByIds(initialWordIds)
                orderWordsByIds(initialWordIds, words)
            }
            loadData(
                initialLearnedCount = initialLearnedCount,
                initialWordList = initialWordList
            )
        }
    }

    fun loadData(
        initialLearnedCount: Int,
        initialWordList: List<Word>
    ) {
        val loadKey = buildSessionLoadKey(
            sessionTypeValue = sessionType,
            sessionWordCount = sessionWordCount,
            initialLearnedCount = initialLearnedCount,
            words = initialWordList
        )
        if (sessionLoadKey == loadKey) return
        sessionLoadKey = loadKey
        words = initialWordList
        wordsById = initialWordList.associateBy { it.id }
        prefillLearnedCount = initialLearnedCount
        sessionTimer.reset()
        trackingEnabled = true
        sessionFinished = false
        sessionEngine = LearningSessionEngine(initialWordList.map { it.id })
        applySessionSnapshot(
            snapshot = sessionEngine?.snapshot(),
            learningState = LearningState.TEST
        )
        if (initialWordList.isEmpty()) {
            finishSession()
        }
    }

    fun onLearningPageVisible() {
        if (!trackingEnabled) return
        sessionTimer.start()
    }

    fun onLearningPageHidden() {
        val durationMs = sessionTimer.pause()
        if (durationMs <= 0L) return
        viewModelScope.launch {
            studyStatsFacade.addStudyDuration(durationMs)
        }
    }

    fun requestExitLearningConfirm() {
        showConfirmDialog(
            action = ACTION_EXIT_LEARNING,
            title = resourceProvider.getString(R.string.learning_exit_confirm_title),
            message = resourceProvider.getString(R.string.learning_exit_confirm_message),
            confirmText = resourceProvider.getString(R.string.learning_exit_confirm_action),
            cancelText = resourceProvider.getString(R.string.learning_exit_confirm_cancel)
        )
    }

    fun handleUserAnswer(isCorrect: Boolean) {
        val engine = sessionEngine ?: return
        val result = engine.submitAnswer(isCorrect)
        applySessionSnapshot(
            snapshot = result.snapshot,
            learningState = LearningState.TEST
        )
        result.completedWordId?.let(wordsById::get)?.let { completedWord ->
            persistCompletedWord(completedWord, markAsMastered = false)
        }
        val answeredWord = result.snapshot.currentWordId?.let(wordsById::get)
        if (answeredWord != null) {
            viewModelScope.launch {
                val bookId = resolveCurrentBookId() ?: return@launch
                recordWordAnswerResult(bookId, answeredWord, isCorrect)
            }
        }
    }

    fun toNext() {
        val engine = sessionEngine ?: return
        if (uiState.value.learningState == LearningState.TEST) {
            if (!uiState.value.isAnswered) return
            toDetail()
            return
        }

        val nextSnapshot = engine.moveToNext()
        if (nextSnapshot.isFinished && nextSnapshot.currentWordId == null) {
            finishSession()
            return
        }
        applySessionSnapshot(
            snapshot = nextSnapshot,
            learningState = LearningState.TEST
        )
    }

    fun toDetail() {
        applySessionSnapshot(
            snapshot = sessionEngine?.snapshot(),
            learningState = LearningState.DETAIL
        )
    }

    fun onSetPronunciationType(type: PronunciationType) {
        pronunciationType = type
        updateUiState {
            it.copy(
                pronunciationType = type,
                pronunciationText = resolvePronunciation(it.currentWord)
            )
        }
    }

    fun onSetWordAsMastered() {
        val engine = sessionEngine ?: return
        val currentWord = uiState.value.currentWord ?: return
        val result = engine.markCurrentWordMastered()
        if (result.completedWordId != null) {
            persistCompletedWord(currentWord, markAsMastered = true)
        }

        val nextSnapshot = engine.moveToNext()
        if (nextSnapshot.isFinished && nextSnapshot.currentWordId == null) {
            finishSession()
        } else {
            applySessionSnapshot(
                snapshot = nextSnapshot,
                learningState = LearningState.TEST
            )
        }
    }

    fun onWordShared() {
        if (uiState.value.currentWord == null) {
            showToast(resourceProvider.getString(R.string.learning_share_empty_content))
            return
        }
        emitEffect(
            LearningShareEffect.ShowWordShareSheet(
                actions = listOf(
                    LearningShareActionItem(
                        action = LearningShareAction.Copy,
                        title = resourceProvider.getString(R.string.learning_share_action_copy)
                    )
                )
            )
        )
    }

    fun onWordShareActionClicked(action: LearningShareAction) {
        val currentWord = uiState.value.currentWord
        if (currentWord == null) {
            showToast(resourceProvider.getString(R.string.learning_share_empty_content))
            return
        }
        when (action) {
            LearningShareAction.Copy -> {
                viewModelScope.launch {
                    val detail = withContext(Dispatchers.IO) {
                        com.chen.memorizewords.domain.word.query.WordDetail(
                            word = currentWord,
                            definitions = wordReadFacade.getWordDefinitions(currentWord.id),
                            examples = wordReadFacade.getWordExamples(currentWord.id),
                            roots = emptyList(),
                            forms = emptyList()
                        )
                    }
                    emitEffect(
                        LearningShareEffect.CopyWordShareText(
                            text = buildLearningWordShareText(
                                detail = detail,
                                labels = LearningWordShareLabels(
                                    usPhoneticLabel = resourceProvider.getString(R.string.learning_share_us_phonetic),
                                    ukPhoneticLabel = resourceProvider.getString(R.string.learning_share_uk_phonetic),
                                    definitionsTitle = resourceProvider.getString(R.string.learning_share_definitions_title),
                                    examplesTitle = resourceProvider.getString(R.string.learning_share_examples_title),
                                    emptyPhonetic = resourceProvider.getString(R.string.learning_share_empty_phonetic),
                                    emptyDefinitions = resourceProvider.getString(R.string.learning_share_empty_definitions),
                                    emptyExamples = resourceProvider.getString(R.string.learning_share_empty_examples)
                                )
                            )
                        )
                    )
                }
            }
        }
    }

    fun onWordShareCopied() {
        showToast(resourceProvider.getString(R.string.learning_share_copied))
    }

    fun onFavoriteClicked() {
        viewModelScope.launch {
            uiState.value.currentWord?.let { word ->
                toggleFavorite(word)
                val favorite = isFavorite(word.id)
                updateUiState { it.copy(isFavorite = favorite) }
            }
        }
    }

    fun onExamPracticeClicked() {
        val currentWord = uiState.value.currentWord ?: return
        navigateRoute(
            Route.ToWordExamPractice(
                wordId = currentWord.id,
                wordText = currentWord.word
            )
        )
    }

    private fun resolvePronunciation(word: Word?): String {
        return when (pronunciationType) {
            PronunciationType.US -> word?.phoneticUS.orEmpty()
            PronunciationType.UK -> word?.phoneticUK.orEmpty()
        }
    }

    private fun applySessionSnapshot(
        snapshot: LearningSessionEngine.SessionSnapshot?,
        learningState: LearningState
    ) {
        val safeSnapshot = snapshot ?: emptySnapshot()
        val currentWord = safeSnapshot.currentWordId?.let(wordsById::get)
        updateUiState {
            it.copy(
                learningState = learningState,
                currentWord = currentWord,
                learnedWordsCount = prefillLearnedCount + safeSnapshot.learnedWordsCount,
                totalWordsCount = prefillLearnedCount + safeSnapshot.totalWordsCount,
                currentTestMode = safeSnapshot.currentTestMode,
                showWordSurface = shouldShowWordSurface(
                    learningState,
                    safeSnapshot.currentTestMode
                ),
                isWrongTrackWord = safeSnapshot.isWrongTrackWord,
                isAnswered = safeSnapshot.isAnswered,
                buttonText = resolveButtonText(
                    learningState = learningState,
                    isAnswered = safeSnapshot.isAnswered,
                    isCurrentWordCompleted = safeSnapshot.isCurrentWordCompleted,
                    isSessionFinished = safeSnapshot.isFinished
                ),
                pronunciationType = pronunciationType,
                questionToken = safeSnapshot.questionToken
            )
        }
        updateCurrentWordMeta(currentWord)
    }

    private fun updateCurrentWordMeta(word: Word?) {
        val wordId = word?.id
        viewModelScope.launch {
            val favorite = if (wordId != null) isFavorite(wordId) else false
            val pronunciationResult = resolvePronunciation(word)
            updateUiState {
                if (it.currentWord?.id != wordId) {
                    return@updateUiState it
                }
                it.copy(
                    isFavorite = favorite,
                    pronunciationText = pronunciationResult
                )
            }
        }
    }

    private fun finishSession() {
        if (sessionFinished) return
        sessionFinished = true
        val snapshot = sessionEngine?.snapshot()
        val studyDurationMs = sessionTimer.finish()
        trackingEnabled = false

        viewModelScope.launch {
            completionPersistenceGate.awaitPending()
            val route = resolveLearningFinishRoute(
                sessionTypeValue = sessionType,
                sessionWordCount = sessionWordCount,
                answeredCount = snapshot?.answeredCount ?: 0,
                correctCount = snapshot?.correctCount ?: 0,
                wrongCount = snapshot?.wrongCount ?: 0,
                studyDurationMs = studyDurationMs,
                wordIds = words.map { it.id },
                addStudyDuration = { durationMs ->
                    withContext(Dispatchers.IO) {
                        studyStatsFacade.addStudyDuration(durationMs)
                    }
                },
                getTodayCheckInEntryState = {
                    withContext(Dispatchers.IO) {
                        studyStatsFacade.getTodayCheckInEntryState()
                    }
                }
            )
            navigateRoute(route)
        }
    }

    private fun emptySnapshot(): LearningSessionEngine.SessionSnapshot {
        return LearningSessionEngine.SessionSnapshot(
            currentWordId = null,
            currentTestMode = LearningTestMode.MEANING_CHOICE,
            isWrongTrackWord = false,
            isAnswered = false,
            isCurrentWordCompleted = false,
            learnedWordsCount = 0,
            totalWordsCount = 0,
            answeredCount = 0,
            correctCount = 0,
            wrongCount = 0,
            isFinished = true,
            questionToken = 0
        )
    }

    private fun updateUiState(block: (LearningUiState) -> LearningUiState) {
        _uiState.update(block)
    }

    private fun persistCompletedWord(
        word: Word,
        markAsMastered: Boolean
    ) {
        completionPersistenceGate.launch(viewModelScope) {
            val bookId = resolveCurrentBookId() ?: return@launch
            if (markAsMastered) {
                setWordAsMastered(
                    bookId,
                    word,
                    isNewWord = resolveLearningRecordIsNewWord(sessionType)
                )
            } else {
                markWordAsLearned(bookId, word, LEARN_QUALITY_CORRECT)
            }
        }
    }

    private suspend fun resolveCurrentBookId(): Long? {
        currentBookId?.takeIf { it > 0L }?.let { return it }
        val resolved = getCurrentWordBookSelectionIdUseCase()
            ?: getCurrentWordBookUseCase()?.id
        currentBookId = resolved
        return resolved?.takeIf { it > 0L }
    }
}

internal fun orderWordsByIds(ids: List<Long>, words: List<Word>): List<Word> {
    val wordsById = words.associateBy { it.id }
    return ids.mapNotNull(wordsById::get)
}

internal data class LearningAutoPlayWordKey(
    val learningState: LearningViewModel.LearningState,
    val wordId: Long,
    val questionToken: Int
)

internal fun resolveLearningAutoPlayWordKey(
    state: LearningViewModel.LearningUiState
): LearningAutoPlayWordKey? {
    val word = state.currentWord ?: return null
    if (!state.showWordSurface) return null
    if (word.word.isBlank()) return null
    return LearningAutoPlayWordKey(
        learningState = state.learningState,
        wordId = word.id,
        questionToken = state.questionToken
    )
}

internal class LearningCompletionPersistenceGate {
    private val lock = Any()
    private val pendingJobs = mutableSetOf<Job>()

    fun launch(
        scope: CoroutineScope,
        block: suspend () -> Unit
    ): Job {
        val job = scope.launch(start = CoroutineStart.LAZY) {
            block()
        }
        synchronized(lock) {
            pendingJobs += job
        }
        job.invokeOnCompletion {
            synchronized(lock) {
                pendingJobs -= job
            }
        }
        job.start()
        return job
    }

    suspend fun awaitPending() {
        synchronized(lock) {
            pendingJobs.toList()
        }.joinAll()
    }
}

internal fun shouldNavigateToCheckIn(
    sessionType: LearningSessionType,
    state: TodayCheckInEntryState
): Boolean {
    return when (sessionType) {
        LearningSessionType.NEW,
        LearningSessionType.REVIEW -> state.shouldNavigate
    }
}

internal fun resolveLearningRecordIsNewWord(sessionTypeValue: Int): Boolean {
    return when (sessionTypeValue) {
        LearningSessionTypes.REVIEW -> false
        LearningSessionTypes.NEW -> true
        else -> true
    }
}

internal suspend fun resolveLearningFinishRoute(
    sessionTypeValue: Int,
    sessionWordCount: Int,
    answeredCount: Int,
    correctCount: Int,
    wrongCount: Int,
    studyDurationMs: Long,
    wordIds: List<Long>,
    addStudyDuration: suspend (Long) -> Unit,
    getTodayCheckInEntryState: suspend () -> TodayCheckInEntryState
): LearningViewModel.Route {
    addStudyDuration(studyDurationMs)
    val sessionType = LearningSessionType.fromValue(sessionTypeValue)
    val state = getTodayCheckInEntryState()
    return if (shouldNavigateToCheckIn(sessionType, state)) {
        LearningViewModel.Route.ToCheckIn
    } else {
        LearningViewModel.Route.ToLearningDone(
            wordIds = wordIds.toLongArray(),
            sessionType = sessionTypeValue,
            sessionWordCount = sessionWordCount,
            answeredCount = answeredCount,
            correctCount = correctCount,
            wrongCount = wrongCount,
            studyDurationMs = studyDurationMs
        )
    }
}
