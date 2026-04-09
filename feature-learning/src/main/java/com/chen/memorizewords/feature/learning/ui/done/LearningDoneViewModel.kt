package com.chen.memorizewords.feature.learning.ui.done

import androidx.lifecycle.viewModelScope
import com.chen.memorizewords.core.common.resource.ResourceProvider
import com.chen.memorizewords.core.ui.vm.BaseViewModel
import com.chen.memorizewords.domain.model.learning.LearningSessionRequest
import com.chen.memorizewords.domain.orchestrator.learning.LearningSessionFacade
import com.chen.memorizewords.domain.query.word.WordReadFacade
import com.chen.memorizewords.domain.service.study.StudyStatsFacade
import com.chen.memorizewords.domain.usecase.wordbook.GetCurrentWordBookUseCase
import com.chen.memorizewords.domain.usecase.wordbook.GetStudyPlanUseCase
import com.chen.memorizewords.domain.model.study.record.TodayCheckInEntryState
import com.chen.memorizewords.domain.model.words.WordListRow
import com.chen.memorizewords.domain.model.words.enums.PartOfSpeech
import com.chen.memorizewords.domain.model.words.word.Word
import com.chen.memorizewords.feature.learning.R
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class LearningDoneViewModel @Inject constructor(
    private val wordReadFacade: WordReadFacade,
    private val learningSessionFacade: LearningSessionFacade,
    private val getCurrentWordBookUseCase: GetCurrentWordBookUseCase,
    private val getStudyPlanUseCase: GetStudyPlanUseCase,
    private val studyStatsFacade: StudyStatsFacade,
    private val resourceProvider: ResourceProvider
) : BaseViewModel() {

    data class LearningDoneUiState(
        val title: String = "",
        val subtitle: String = "",
        val tagText: String = "",
        val completedCountText: String = "0",
        val durationText: String = "",
        val accuracyText: String = "",
        val qualityText: String = "",
        val answeredText: String = "0",
        val wrongText: String = "0",
        val efficiencyText: String = ""
    )

    enum class QualityGrade(val label: String) {
        A("A"),
        B("B"),
        C("C")
    }

    sealed interface Route {
        data class ToLearning(
            val request: LearningSessionRequest,
            val replaceCurrent: Boolean = true
        ) : Route

        data object ToCheckIn : Route
    }

    private val _uiState = MutableStateFlow(LearningDoneUiState())
    val uiState: StateFlow<LearningDoneUiState> = _uiState.asStateFlow()

    private val _wordRows = MutableStateFlow<List<WordListRow>>(emptyList())
    val wordRows: StateFlow<List<WordListRow>> = _wordRows.asStateFlow()

    private var sessionTypeValue: Int = LearningSessionType.NEW.value
    private var sessionWordCount: Int = 0
    private var sessionWordIds: List<Long> = emptyList()
    private var loadedKey: String? = null
    private var hasRequestedCheckInNavigation = false

    fun loadSession(
        sessionTypeValue: Int,
        sessionWordCount: Int,
        answeredCount: Int,
        correctCount: Int,
        wrongCount: Int,
        studyDurationMs: Long,
        wordIds: List<Long>
    ) {
        val key =
            "${sessionTypeValue}_${sessionWordCount}_${answeredCount}_${correctCount}_${wrongCount}_${studyDurationMs}_${wordIds.size}"
        if (loadedKey == key) return
        loadedKey = key
        hasRequestedCheckInNavigation = false
        this.sessionTypeValue = sessionTypeValue
        this.sessionWordCount = sessionWordCount
        this.sessionWordIds = wordIds

        val sessionType = LearningSessionType.fromValue(sessionTypeValue)
        val subtitleRes = if (sessionType == LearningSessionType.REVIEW) {
            R.string.learning_done_subtitle_review
        } else {
            R.string.learning_done_subtitle_new
        }
        val tagRes = if (sessionType == LearningSessionType.REVIEW) {
            R.string.learning_done_tag_review
        } else {
            R.string.learning_done_tag_new
        }

        val completedCount = wordIds.size.takeIf { it > 0 } ?: sessionWordCount
        val accuracyPercent = calculateAccuracyPercent(correctCount, wrongCount)
        val qualityGrade = resolveQualityGrade(accuracyPercent)
        val durationText = formatDuration(studyDurationMs)
        val accuracyText = resourceProvider.getString(
            R.string.learning_done_accuracy_format,
            accuracyPercent
        )

        val durationMinutes = (studyDurationMs / 60_000L).coerceAtLeast(1L)
        val efficiency = completedCount.toFloat() / durationMinutes.toFloat()
        val efficiencyText = resourceProvider.getString(
            R.string.learning_done_efficiency_format,
            efficiency
        )

        _uiState.value = LearningDoneUiState(
            title = resourceProvider.getString(R.string.learning_done_title),
            subtitle = resourceProvider.getString(subtitleRes),
            tagText = resourceProvider.getString(tagRes),
            completedCountText = completedCount.toString(),
            durationText = durationText,
            accuracyText = accuracyText,
            qualityText = qualityGrade.label,
            answeredText = answeredCount.toString(),
            wrongText = wrongCount.toString(),
            efficiencyText = efficiencyText
        )

        viewModelScope.launch {
            val words = withContext(Dispatchers.IO) { wordReadFacade.getWordsByIds(wordIds) }
            _wordRows.value = buildWordRows(words)
        }

        viewModelScope.launch {
            val state = withContext(Dispatchers.IO) { studyStatsFacade.getTodayCheckInEntryState() }
            if (shouldNavigateToCheckIn(sessionType, state, hasRequestedCheckInNavigation)) {
                hasRequestedCheckInNavigation = true
                navigateRoute(Route.ToCheckIn)
            }
        }
    }

    fun onContinueLearning() {
        viewModelScope.launch {
            val wordBook = withContext(Dispatchers.IO) { getCurrentWordBookUseCase() }
            if (wordBook == null) {
                showToast(resourceProvider.getString(R.string.learning_done_no_book))
                return@launch
            }

            val plan = withContext(Dispatchers.IO) { getStudyPlanUseCase() }
            val request = buildContinueLearningRequest(sessionTypeValue, sessionWordCount)
            val excludeIds = sessionWordIds.toSet()
            val nextSessionRequest = withContext(Dispatchers.IO) {
                when (request.sessionType) {
                    LearningSessionType.NEW -> learningSessionFacade.createNewSessionRequest(
                        bookId = wordBook.id,
                        count = request.sessionWordCount,
                        orderType = plan.wordOrderType,
                        excludeIds = excludeIds
                    )

                    LearningSessionType.REVIEW -> learningSessionFacade.createReviewSessionRequest(
                        bookId = wordBook.id,
                        count = request.sessionWordCount,
                        orderType = plan.wordOrderType,
                        excludeIds = excludeIds
                    )
                }
            }

            if (nextSessionRequest == null) {
                showToast(resourceProvider.getString(R.string.learning_done_no_more_words))
                return@launch
            }

            navigateRoute(Route.ToLearning(request = nextSessionRequest))
        }
    }

    private suspend fun buildWordRows(words: List<Word>): List<WordListRow> {
        return withContext(Dispatchers.IO) {
            words.map { word ->
                val detail = wordReadFacade.getWordDetail(word)
                val definition = detail.definitions.firstOrNull()
                WordListRow(
                    wordId = word.id,
                    word = word.word,
                    phonetic = word.phoneticUS ?: word.phoneticUK,
                    partOfSpeech = definition?.partOfSpeech ?: PartOfSpeech.OTHER,
                    meanings = definition?.meaningChinese ?: ""
                )
            }
        }
    }

    private fun formatDuration(durationMs: Long): String {
        val totalSeconds = (durationMs / 1000L).coerceAtLeast(0L)
        val hours = totalSeconds / 3600L
        val minutes = (totalSeconds % 3600L) / 60L
        return if (hours > 0L) {
            resourceProvider.getString(
                R.string.learning_done_duration_hours_minutes,
                hours,
                minutes
            )
        } else {
            resourceProvider.getString(R.string.learning_done_duration_minutes, minutes)
        }
    }

    companion object {
        fun calculateAccuracyPercent(correctCount: Int, wrongCount: Int): Float {
            val total = correctCount + wrongCount
            if (total <= 0) return 0f
            return (correctCount.toFloat() / total.toFloat()) * 100f
        }

        fun resolveQualityGrade(accuracyPercent: Float): QualityGrade {
            return when {
                accuracyPercent >= 90f -> QualityGrade.A
                accuracyPercent >= 75f -> QualityGrade.B
                else -> QualityGrade.C
            }
        }

        fun buildContinueLearningRequest(
            sessionTypeValue: Int,
            sessionWordCount: Int
        ): ContinueLearningRequest {
            val sessionType = LearningSessionType.fromValue(sessionTypeValue)
            return ContinueLearningRequest(
                sessionType = sessionType,
                sessionWordCount = sessionWordCount
            )
        }
    }
}

internal fun shouldNavigateToCheckIn(
    sessionType: LearningSessionType,
    state: TodayCheckInEntryState,
    hasRequestedCheckInNavigation: Boolean
): Boolean {
    return when (sessionType) {
        LearningSessionType.NEW,
        LearningSessionType.REVIEW -> state.shouldNavigate && !hasRequestedCheckInNavigation
    }
}

enum class LearningSessionType(val value: Int) {
    NEW(0),
    REVIEW(1);

    companion object {
        fun fromValue(value: Int): LearningSessionType {
            return if (value == REVIEW.value) REVIEW else NEW
        }
    }
}

data class ContinueLearningRequest(
    val sessionType: LearningSessionType,
    val sessionWordCount: Int
)
