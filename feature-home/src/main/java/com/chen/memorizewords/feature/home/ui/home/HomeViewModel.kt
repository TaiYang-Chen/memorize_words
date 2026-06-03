package com.chen.memorizewords.feature.home.ui.home

import android.text.Html
import android.text.Spanned
import androidx.lifecycle.viewModelScope
import com.chen.memorizewords.core.navigation.AppRoute
import com.chen.memorizewords.core.navigation.LearningSessionRequest
import com.chen.memorizewords.core.common.resource.ResourceProvider
import com.chen.memorizewords.core.ui.vm.BaseViewModel
import com.chen.memorizewords.core.ui.vm.UiEvent
import com.chen.memorizewords.domain.study.model.learning.LearningSessionRequest as DomainLearningSessionRequest
import com.chen.memorizewords.domain.wordbook.model.study.StudyPlan
import com.chen.memorizewords.domain.sync.model.SyncBannerState
import com.chen.memorizewords.domain.account.model.user.User
import com.chen.memorizewords.domain.word.model.word.Word
import com.chen.memorizewords.domain.wordbook.model.WordBookInfo
import com.chen.memorizewords.domain.study.orchestrator.learning.LearningSessionFacade
import com.chen.memorizewords.domain.study.service.StudyStatsFacade
import com.chen.memorizewords.domain.sync.service.SyncFacade
import com.chen.memorizewords.domain.account.usecase.user.GetCurrentUserUseCase
import com.chen.memorizewords.domain.wordbook.usecase.GetCurrentWordBookInfoFlowUseCase
import com.chen.memorizewords.domain.wordbook.usecase.GetStudyPlanFlowUseCase
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
        data object ToPendingSyncDetails : Route
    }

    val user: MutableStateFlow<User?> = MutableStateFlow(null)

    val isPreparingLearning: MutableStateFlow<Boolean> = MutableStateFlow(false)

    val learningButtonEnabled: StateFlow<Boolean> =
        isPreparingLearning
            .map { preparing -> !preparing }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    private var pendingBoostBookId: Long? = null
    private var pendingBoostPlan: StudyPlan? = null

    val wordBookInfo: StateFlow<WordBookInfo?> =
        getCurrentWordBookInfoFlowUseCase()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val studyPlan: StateFlow<StudyPlan> =
        getStudyPlanFlowUseCase()
            .map { plan -> plan ?: StudyPlan() }
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
        combine(todayNewCount, studyPlan, isPreparingLearning) { newCount, plan, preparing ->
            if (preparing) {
                resourceProvider.getString(R.string.home_learning_prepare_loading)
            } else {
                textFormatter.formatLearnButtonText(newCount, plan)
            }
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
    }

    fun toWordBookActivity() {
        navigate(AppRoute.WordBook())
    }

    fun onSyncBannerClicked() {
        if (!canOpenPendingSyncDetails(syncBannerState.value)) {
            return
        }
        navigateRoute(Route.ToPendingSyncDetails)
    }

    fun onStartLearningClick() {
        if (isPreparingLearning.value) {
            return
        }
        val bookInfo = wordBookInfo.value
        if (bookInfo == null) {
            restoreLearningPrerequisitesAndStart()
            return
        }
        startLearning(bookInfo.bookId, studyPlan.value)
    }

    private fun restoreLearningPrerequisitesAndStart() {
        viewModelScope.launch {
            isPreparingLearning.value = true
            try {
                syncFacade.restoreLearningPrerequisites()
                    .onSuccess { snapshot ->
                        startLearning(snapshot.selectedBookId, snapshot.studyPlan)
                    }
                    .onFailure {
                        showToast(resourceProvider.getString(R.string.home_learning_prepare_failed))
                    }
            } finally {
                isPreparingLearning.value = false
            }
        }
    }

    private fun startLearning(bookId: Long, plan: StudyPlan) {
        val completedNewPlan = plan.dailyNewCount > 0 && todayNewCount.value >= plan.dailyNewCount
        if (!completedNewPlan) {
            pendingBoostBookId = null
            pendingBoostPlan = null
            navigateToNewLearning(bookId, plan, plan.dailyNewCount)
            return
        }

        pendingBoostBookId = bookId
        pendingBoostPlan = plan

        emitEvent(
            UiEvent.Dialog.CustomConfirmDialog(
                custom = CUSTOM_DIALOG_HOME_BOOST_NEW_WORDS
            )
        )
    }

    fun onBoostNewWordsSelected(selected: Int) {
        val selectedCount = selected.takeIf { it > 0 } ?: DEFAULT_BOOST_NEW_WORDS
        val bookId = pendingBoostBookId ?: wordBookInfo.value?.bookId
        val plan = pendingBoostPlan ?: studyPlan.value
        pendingBoostBookId = null
        pendingBoostPlan = null
        if (bookId == null) {
            showToast(resourceProvider.getString(R.string.home_practice_no_book))
            return
        }
        navigateToNewLearning(bookId, plan, selectedCount)
    }

    fun toLearningNewActivity() {
        val bookInfo = wordBookInfo.value
        if (bookInfo == null) {
            showToast(resourceProvider.getString(R.string.home_practice_no_book))
            return
        }
        navigateToNewLearning(bookInfo.bookId, studyPlan.value, studyPlan.value.dailyNewCount)
    }

    fun toLearningReviewActivity() {
        val bookInfo = wordBookInfo.value
        if (bookInfo == null) {
            showToast(resourceProvider.getString(R.string.home_practice_no_book))
            return
        }
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

    private fun navigateToNewLearning(count: Int) {
        val bookInfo = wordBookInfo.value
        if (bookInfo == null) {
            showToast(resourceProvider.getString(R.string.home_practice_no_book))
            return
        }
        navigateToNewLearning(bookInfo.bookId, studyPlan.value, count)
    }

    private fun navigateToNewLearning(bookId: Long, plan: StudyPlan, count: Int) {
        viewModelScope.launch {
            val route = learningLauncher.createNewRoute(
                bookId = bookId,
                count = count,
                orderType = plan.wordOrderType
            )
            if (route == null) {
                showToast(resourceProvider.getString(R.string.home_learning_no_words))
                return@launch
            }
            navigateRoute(route)
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
    request: DomainLearningSessionRequest?
): AppRoute.Learning? {
    val safeRequest = request ?: return null
    if (safeRequest.wordIds.isEmpty()) return null
    return AppRoute.Learning(
        wordIds = safeRequest.wordIds,
        sessionType = safeRequest.sessionType,
        sessionWordCount = safeRequest.sessionWordCount
    )
}

internal fun createLearningRoute(
    learningWords: List<Word>,
    sessionType: Int,
    sessionWordCount: Int
): AppRoute.Learning? {
    if (learningWords.isEmpty()) return null
    return AppRoute.Learning(
        wordIds = learningWords.map { it.id },
        sessionType = sessionType,
        sessionWordCount = sessionWordCount
    )
}

internal object LearningSessionTypeContract {
    const val NEW: Int = 0
    const val REVIEW: Int = 1
}

internal fun canOpenPendingSyncDetails(state: SyncBannerState): Boolean {
    return state != SyncBannerState.Hidden
}
