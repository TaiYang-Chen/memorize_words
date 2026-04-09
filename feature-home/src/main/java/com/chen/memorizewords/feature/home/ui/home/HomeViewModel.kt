package com.chen.memorizewords.feature.home.ui.home

import android.text.Html
import android.text.Spanned
import androidx.lifecycle.viewModelScope
import com.chen.memorizewords.core.common.resource.ResourceProvider
import com.chen.memorizewords.core.ui.vm.BaseViewModel
import com.chen.memorizewords.core.ui.vm.UiEvent
import com.chen.memorizewords.domain.model.learning.LearningSessionRequest
import com.chen.memorizewords.domain.model.study.StudyPlan
import com.chen.memorizewords.domain.model.sync.SyncBannerState
import com.chen.memorizewords.domain.model.user.User
import com.chen.memorizewords.domain.model.words.word.Word
import com.chen.memorizewords.domain.model.wordbook.WordBookInfo
import com.chen.memorizewords.domain.orchestrator.learning.LearningSessionFacade
import com.chen.memorizewords.domain.service.study.StudyStatsFacade
import com.chen.memorizewords.domain.service.sync.SyncFacade
import com.chen.memorizewords.domain.usecase.user.GetCurrentUserUseCase
import com.chen.memorizewords.domain.usecase.wordbook.GetCurrentWordBookInfoFlowUseCase
import com.chen.memorizewords.domain.usecase.wordbook.GetStudyPlanFlowUseCase
import com.chen.memorizewords.feature.home.R
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val getCurrentUserUseCase: GetCurrentUserUseCase,
    getCurrentWordBookInfoFlowUseCase: GetCurrentWordBookInfoFlowUseCase,
    getStudyPlanFlowUseCase: GetStudyPlanFlowUseCase,
    private val learningSessionFacade: LearningSessionFacade,
    private val studyStatsFacade: StudyStatsFacade,
    private val syncFacade: SyncFacade,
    private val resourceProvider: ResourceProvider
) : BaseViewModel() {

    private val textFormatter = HomeTextFormatter(resourceProvider)
    private val learningLauncher = HomeLearningLauncher(learningSessionFacade)

    companion object {
        const val CUSTOM_DIALOG_HOME_BOOST_NEW_WORDS = "home_boost_new_words"
        const val DEFAULT_BOOST_NEW_WORDS = 5
    }

    sealed interface Route {
        data object ToWordBook : Route
        data class ToLearning(
            val request: LearningSessionRequest,
            val learningWords: List<Word> = emptyList()
        ) : Route {
            val sessionType: Int
                get() = request.sessionType

            val sessionWordCount: Int
                get() = request.sessionWordCount
        }
    }

    val user: MutableStateFlow<User?> = MutableStateFlow(null)

    val wordBookInfo: StateFlow<WordBookInfo?> =
        getCurrentWordBookInfoFlowUseCase()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val studyPlan: StateFlow<StudyPlan> =
        getStudyPlanFlowUseCase()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StudyPlan())

    val studyTotalDayCount: StateFlow<Int> =
        studyStatsFacade.getStudyTotalDayCount()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val todayNewCount: StateFlow<Int> =
        studyStatsFacade.getTodayNewWordCount()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val todayReviewCount: StateFlow<Int> =
        studyStatsFacade.getTodayReviewWordCount()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val learnProcess: StateFlow<Int> =
        combine(todayNewCount, todayReviewCount, studyPlan) { newCount, reviewCount, plan ->
            calculateLearnProgress(
                todayNewCount = newCount,
                todayReviewCount = reviewCount,
                plan = plan
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val learnButtonText: StateFlow<String> =
        combine(todayNewCount, studyPlan) { newCount, plan ->
            textFormatter.formatLearnButtonText(newCount, plan)
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            resourceProvider.getString(R.string.home_learn_button_start)
        )

    val todayStudyDurationText: StateFlow<String> =
        studyStatsFacade.getTodayStudyDurationMs()
            .map { textFormatter.formatDuration(it) }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                resourceProvider.getString(R.string.home_duration_minutes, 0)
            )

    val learnPlanText: StateFlow<Spanned> =
        combine(todayNewCount, todayReviewCount, studyPlan) { newCount, reviewCount, plan ->
            textFormatter.formatLearnPlanText(newCount, reviewCount, plan)
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            textFormatter.defaultLearnPlanText()
        )

    val syncBannerState: StateFlow<SyncBannerState> =
        syncFacade.observeSyncBannerState()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SyncBannerState.Hidden)

    val syncBannerText: StateFlow<String> =
        syncBannerState
            .map(textFormatter::formatSyncBannerText)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    init {
        viewModelScope.launch {
            user.value = getCurrentUserUseCase()
        }
        syncFacade.triggerDrain()
    }

    fun toWordBookActivity() {
        navigateRoute(Route.ToWordBook)
    }

    fun onStartLearningClick() {
        val plan = studyPlan.value
        val completedNewPlan = plan.dailyNewCount > 0 && todayNewCount.value >= plan.dailyNewCount
        if (!completedNewPlan) {
            toLearningNewActivity()
            return
        }

        emitEvent(
            UiEvent.Dialog.CustomConfirmDialog(
                custom = CUSTOM_DIALOG_HOME_BOOST_NEW_WORDS
            )
        )
    }

    fun onBoostNewWordsSelected(selected: Int) {
        val selectedCount = selected.takeIf { it > 0 } ?: DEFAULT_BOOST_NEW_WORDS
        navigateToNewLearning(selectedCount)
    }

    fun toLearningNewActivity() {
        navigateToNewLearning(studyPlan.value.dailyNewCount)
    }

    fun toLearningReviewActivity() {
        wordBookInfo.value?.let { bookInfo ->
            viewModelScope.launch {
                val route = learningLauncher.createReviewRoute(
                    bookId = bookInfo.bookId,
                    count = resolveReviewWordCount(studyPlan.value),
                    orderType = studyPlan.value.wordOrderType
                )
                if (route == null) {
                    showToast(resourceProvider.getString(R.string.home_learning_no_words))
                    return@launch
                }
                navigateRoute(route)
            }
        }
    }

    private fun navigateToNewLearning(count: Int) {
        wordBookInfo.value?.let { bookInfo ->
            viewModelScope.launch {
                val route = learningLauncher.createNewRoute(
                    bookId = bookInfo.bookId,
                    count = count,
                    orderType = studyPlan.value.wordOrderType
                )
                if (route == null) {
                    showToast(resourceProvider.getString(R.string.home_learning_no_words))
                    return@launch
                }
                navigateRoute(route)
            }
        }
    }
}

internal fun calculateLearnProgress(
    todayNewCount: Int,
    todayReviewCount: Int,
    plan: StudyPlan
): Int {
    val boundedNewCount = minOf(todayNewCount, plan.dailyNewCount)
    val boundedReviewCount = minOf(todayReviewCount, plan.dailyReviewCount)
    val totalPlan = plan.dailyNewCount + plan.dailyReviewCount
    if (totalPlan <= 0) {
        return 0
    }
    return (((boundedNewCount + boundedReviewCount).toFloat() / totalPlan.toFloat()) * 100).toInt()
}

internal fun resolveReviewWordCount(plan: StudyPlan): Int {
    return plan.dailyReviewCount.coerceAtLeast(0)
}

internal fun createLearningRoute(
    request: LearningSessionRequest?
): HomeViewModel.Route.ToLearning? {
    val safeRequest = request ?: return null
    if (safeRequest.wordIds.isEmpty()) return null
    return HomeViewModel.Route.ToLearning(safeRequest)
}

internal fun createLearningRoute(
    learningWords: List<Word>,
    sessionType: Int,
    sessionWordCount: Int
): HomeViewModel.Route.ToLearning? {
    if (learningWords.isEmpty()) return null
    return HomeViewModel.Route.ToLearning(
        request = LearningSessionRequest(
            wordIds = learningWords.map { it.id },
            sessionType = sessionType,
            sessionWordCount = sessionWordCount
        ),
        learningWords = learningWords
    )
}

internal object LearningSessionTypeContract {
    const val NEW: Int = 0
    const val REVIEW: Int = 1
}
