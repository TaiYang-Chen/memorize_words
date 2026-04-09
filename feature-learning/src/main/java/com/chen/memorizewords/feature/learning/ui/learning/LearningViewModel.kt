package com.chen.memorizewords.feature.learning.ui.learning

import androidx.lifecycle.viewModelScope
import com.chen.memorizewords.core.common.resource.ResourceProvider
import com.chen.memorizewords.core.common.session.SessionTimer
import com.chen.memorizewords.core.ui.vm.BaseViewModel
import com.chen.memorizewords.domain.query.word.WordReadFacade
import com.chen.memorizewords.domain.service.study.StudyStatsFacade
import com.chen.memorizewords.domain.usecase.wordbook.GetCurrentWordBookUseCase
import com.chen.memorizewords.domain.model.learning.LearningTestMode
import com.chen.memorizewords.domain.model.wordbook.WordBook
import com.chen.memorizewords.domain.model.words.word.PronunciationType
import com.chen.memorizewords.domain.model.words.word.Word
import com.chen.memorizewords.domain.usecase.word.MarkWordAsLearnedUseCase
import com.chen.memorizewords.domain.usecase.word.study.IsFavoriteUseCase
import com.chen.memorizewords.domain.usecase.word.study.RecordWordAnswerResultUseCase
import com.chen.memorizewords.domain.usecase.word.study.SetWordAsMasteredUseCase
import com.chen.memorizewords.domain.usecase.word.study.ToggleFavoriteUseCase
import com.chen.memorizewords.feature.learning.R
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
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

    var wordBook: WordBook? = null
    var words: List<Word> = emptyList()

    private var prefillLearnedCount: Int = 0
    private var sessionType: Int = 0
    private var sessionWordCount: Int = 0
    private var sessionCoordinator: LearningSessionCoordinator? = null
    private var sessionLoadKey: String? = null

    private var pronunciationType: PronunciationType = PronunciationType.US
    private val sessionTimer = SessionTimer()
    private var trackingEnabled: Boolean = true
    private var sessionFinished: Boolean = false

    init {
        viewModelScope.launch {
            wordBook = getCurrentWordBookUseCase()
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
            loadData(
                initialLearnedCount = initialLearnedCount,
                initialWordList = wordReadFacade.getWordsByIds(initialWordIds)
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
        prefillLearnedCount = initialLearnedCount
        sessionTimer.reset()
        trackingEnabled = true
        sessionFinished = false
        sessionCoordinator = LearningSessionCoordinator(initialWordList)
        applySessionSnapshot(
            snapshot = sessionCoordinator?.snapshot(),
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

    fun handleUserAnswer(isCorrect: Boolean) {
        val coordinator = sessionCoordinator ?: return
        val result = coordinator.submitAnswer(isCorrect)
        applySessionSnapshot(
            snapshot = result.snapshot,
            learningState = LearningState.TEST
        )
        result.newlyCompletedWord?.let { completedWord ->
            wordBook?.let { book ->
                viewModelScope.launch {
                    markWordAsLearned(book.id, completedWord, LEARN_QUALITY_CORRECT)
                }
            }
        }
        wordBook?.let { book ->
            viewModelScope.launch {
                recordWordAnswerResult(book.id, isCorrect)
            }
        }
    }

    fun toNext() {
        val coordinator = sessionCoordinator ?: return
        if (uiState.value.learningState == LearningState.TEST) {
            if (!uiState.value.isAnswered) return
            toDetail()
            return
        }

        val nextSnapshot = coordinator.moveToNext()
        if (nextSnapshot.isFinished && nextSnapshot.currentWord == null) {
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
            snapshot = sessionCoordinator?.snapshot(),
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
        val coordinator = sessionCoordinator ?: return
        val currentWord = uiState.value.currentWord ?: return
        val result = coordinator.markCurrentWordMastered()
        if (result.newlyCompletedWord != null) {
            wordBook?.let { book ->
                viewModelScope.launch {
                    setWordAsMastered(book.id, currentWord)
                }
            }
        }

        val nextSnapshot = coordinator.moveToNext()
        if (nextSnapshot.isFinished && nextSnapshot.currentWord == null) {
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
                        com.chen.memorizewords.domain.query.word.WordDetail(
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
        snapshot: LearningSessionCoordinator.SessionSnapshot?,
        learningState: LearningState
    ) {
        val safeSnapshot = snapshot ?: emptySnapshot()
        updateUiState {
            it.copy(
                learningState = learningState,
                currentWord = safeSnapshot.currentWord,
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
        updateCurrentWordMeta(safeSnapshot.currentWord)
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
        onLearningPageHidden()
        trackingEnabled = false
        val snapshot = sessionCoordinator?.snapshot()
        val studyDurationMs = sessionTimer.finish()
        val payload = Route.ToLearningDone(
            wordIds = words.map { it.id }.toLongArray(),
            sessionType = sessionType,
            sessionWordCount = sessionWordCount,
            answeredCount = snapshot?.answeredCount ?: 0,
            correctCount = snapshot?.correctCount ?: 0,
            wrongCount = snapshot?.wrongCount ?: 0,
            studyDurationMs = studyDurationMs
        )
        navigateRoute(payload)
    }

    private fun emptySnapshot(): LearningSessionCoordinator.SessionSnapshot {
        return LearningSessionCoordinator.SessionSnapshot(
            currentWord = null,
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
}
