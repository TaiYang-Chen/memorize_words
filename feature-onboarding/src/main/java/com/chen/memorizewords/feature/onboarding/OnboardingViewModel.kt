package com.chen.memorizewords.feature.onboarding

import androidx.lifecycle.viewModelScope
import com.chen.memorizewords.core.ui.vm.BaseViewModel
import com.chen.memorizewords.domain.wordbook.model.learning.LearningTestMode
import com.chen.memorizewords.domain.wordbook.model.onboarding.OnboardingError
import com.chen.memorizewords.domain.wordbook.model.onboarding.OnboardingPhase
import com.chen.memorizewords.domain.wordbook.model.onboarding.OnboardingSnapshot
import com.chen.memorizewords.domain.wordbook.model.onboarding.OnboardingStep
import com.chen.memorizewords.domain.wordbook.model.study.StudyPlan
import com.chen.memorizewords.domain.wordbook.model.WordBook
import com.chen.memorizewords.domain.wordbook.repository.WordOrderType
import com.chen.memorizewords.domain.wordbook.service.onboarding.OnboardingCoordinator
import com.chen.memorizewords.domain.wordbook.service.onboarding.OnboardingOperationException
import com.chen.memorizewords.domain.wordbook.usecase.onboarding.GetCurrentOnboardingSnapshotUseCase
import com.chen.memorizewords.domain.wordbook.usecase.onboarding.ObserveCurrentOnboardingSnapshotUseCase
import com.chen.memorizewords.domain.wordbook.usecase.GetStudyPlanFlowUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    getCurrentOnboardingSnapshotUseCase: GetCurrentOnboardingSnapshotUseCase,
    observeCurrentOnboardingSnapshotUseCase: ObserveCurrentOnboardingSnapshotUseCase,
    getStudyPlanFlowUseCase: GetStudyPlanFlowUseCase,
    private val onboardingCoordinator: OnboardingCoordinator
) : BaseViewModel() {

    private val initialSnapshot = getCurrentOnboardingSnapshotUseCase()

    private val _draftStudyPlan = MutableStateFlow(StudyPlan())
    private val _pendingSelectedWordBook = MutableStateFlow<WordBook?>(null)
    private val _selectedWordBook = MutableStateFlow<WordBook?>(null)
    private val _isSubmittingFinalDraft = MutableStateFlow(false)
    private val _planSubmitErrorMessage = MutableStateFlow<String?>(null)

    val snapshot: StateFlow<OnboardingSnapshot> =
        observeCurrentOnboardingSnapshotUseCase()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = initialSnapshot
            )

    val step: StateFlow<OnboardingStep> =
        combine(snapshot, _selectedWordBook) { snapshot, selectedWordBook ->
            when {
                snapshot.phase == OnboardingPhase.COMPLETED -> OnboardingStep.COMPLETED
                selectedWordBook != null -> OnboardingStep.SET_STUDY_PLAN
                else -> OnboardingStep.SELECT_WORD_BOOK
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = if (initialSnapshot.phase == OnboardingPhase.COMPLETED) {
                OnboardingStep.COMPLETED
            } else {
                OnboardingStep.SELECT_WORD_BOOK
            }
        )

    val pendingSelectedWordBook: StateFlow<WordBook?> =
        _pendingSelectedWordBook.asStateFlow()

    val planSubmitErrorMessage: StateFlow<String?> = _planSubmitErrorMessage.asStateFlow()

    val planUiState: StateFlow<OnboardingPlanUiState> =
        combine(
            _selectedWordBook,
            _draftStudyPlan,
            _isSubmittingFinalDraft
        ) { selectedWordBook, draftStudyPlan, isSubmitting ->
            if (selectedWordBook == null) {
                OnboardingPlanUiState.Error(message = REQUIRED_DATA_MESSAGE)
            } else {
                OnboardingPlanUiState.Content(
                    wordBook = selectedWordBook,
                    studyPlan = draftStudyPlan,
                    isSubmitting = isSubmitting
                )
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = OnboardingPlanUiState.Loading
        )

    init {
        viewModelScope.launch {
            getStudyPlanFlowUseCase()
                .take(1)
                .collect { _draftStudyPlan.value = it }
        }
    }

    fun selectWordBook(wordBook: WordBook) {
        _planSubmitErrorMessage.value = null
        _pendingSelectedWordBook.value = wordBook
    }

    fun confirmSelectedWordBook() {
        val pendingSelection = _pendingSelectedWordBook.value ?: return
        _planSubmitErrorMessage.value = null
        _selectedWordBook.value = pendingSelection
    }

    fun updateDailyNewCount(value: Int) {
        _planSubmitErrorMessage.value = null
        _draftStudyPlan.update { current ->
            current.copy(dailyNewCount = value.coerceIn(MIN_DAILY_NEW_COUNT, MAX_DAILY_NEW_COUNT))
        }
    }

    fun updateDailyReviewCount(value: Int) {
        _planSubmitErrorMessage.value = null
        _draftStudyPlan.update { current ->
            current.copy(
                dailyReviewCount = value.coerceIn(
                    MIN_DAILY_REVIEW_COUNT,
                    MAX_DAILY_REVIEW_COUNT
                )
            )
        }
    }

    fun increaseDailyNewCount() {
        updateDailyNewCount(_draftStudyPlan.value.dailyNewCount + 1)
    }

    fun decreaseDailyNewCount() {
        updateDailyNewCount(_draftStudyPlan.value.dailyNewCount - 1)
    }

    fun increaseDailyReviewCount() {
        updateDailyReviewCount(_draftStudyPlan.value.dailyReviewCount + 1)
    }

    fun decreaseDailyReviewCount() {
        updateDailyReviewCount(_draftStudyPlan.value.dailyReviewCount - 1)
    }

    fun applyBalancedSuggestion() {
        _planSubmitErrorMessage.value = null
        _draftStudyPlan.update { current ->
            current.copy(
                dailyNewCount = RECOMMENDED_DAILY_NEW_COUNT,
                dailyReviewCount = RECOMMENDED_DAILY_REVIEW_COUNT
            )
        }
    }

    fun updateTestMode(mode: LearningTestMode) {
        _planSubmitErrorMessage.value = null
        _draftStudyPlan.update { current -> current.copy(testMode = mode) }
    }

    fun updateWordOrderType(type: WordOrderType) {
        _planSubmitErrorMessage.value = null
        _draftStudyPlan.update { current -> current.copy(wordOrderType = type) }
    }

    fun retryPlanLoad() {
        _planSubmitErrorMessage.value = null
    }

    fun backToSelectWordBook() {
        if (_isSubmittingFinalDraft.value) return
        _planSubmitErrorMessage.value = null
        _selectedWordBook.value = null
    }

    fun completeStudyPlan() {
        val currentState = planUiState.value as? OnboardingPlanUiState.Content ?: return
        if (_isSubmittingFinalDraft.value) return
        viewModelScope.launch {
            _isSubmittingFinalDraft.value = true
            _planSubmitErrorMessage.value = null
            onboardingCoordinator.completeOnboarding(
                selectedBook = currentState.wordBook,
                studyPlan = currentState.studyPlan
            ).onFailure { throwable ->
                val message = throwable.toOnboardingMessage()
                _planSubmitErrorMessage.value = message
                showToast(message)
            }
            _isSubmittingFinalDraft.value = false
        }
    }
}

sealed interface OnboardingPlanUiState {
    data object Loading : OnboardingPlanUiState
    data class Error(val message: String) : OnboardingPlanUiState
    data class Content(
        val wordBook: WordBook,
        val studyPlan: StudyPlan,
        val isSubmitting: Boolean
    ) : OnboardingPlanUiState
}

private fun Throwable.toOnboardingMessage(): String {
    val onboardingError = (this as? OnboardingOperationException)?.onboardingError
        ?: OnboardingError.Unknown(message)
    return when (onboardingError) {
        OnboardingError.LocalPersistenceFailed -> "\u4FDD\u5B58\u5931\u8D25\uFF0C\u8BF7\u91CD\u8BD5"
        OnboardingError.RequiredDataUnavailable -> REQUIRED_DATA_MESSAGE
        OnboardingError.SyncDeferred -> "\u64CD\u4F5C\u8FDB\u884C\u4E2D\uFF0C\u8BF7\u7A0D\u5019"
        is OnboardingError.Unknown -> onboardingError.message
            ?: "\u53D1\u751F\u672A\u77E5\u9519\u8BEF\uFF0C\u8BF7\u91CD\u8BD5"
    }
}

private const val REQUIRED_DATA_MESSAGE = "\u8BF7\u5148\u9009\u62E9\u4E00\u672C\u8BCD\u6C47\u4E66"
private const val MIN_DAILY_NEW_COUNT = 1
private const val MAX_DAILY_NEW_COUNT = 80
private const val MIN_DAILY_REVIEW_COUNT = 1
private const val MAX_DAILY_REVIEW_COUNT = 160
private const val RECOMMENDED_DAILY_NEW_COUNT = 15
private const val RECOMMENDED_DAILY_REVIEW_COUNT = 30
