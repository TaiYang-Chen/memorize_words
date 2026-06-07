package com.chen.memorizewords.feature.home.ui.home

import android.text.Spanned
import androidx.lifecycle.viewModelScope
import com.chen.memorizewords.core.navigation.AppRoute
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

internal const val WORD_BOOK_INFO_PLACEHOLDER = "--"

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

    val wordBookTitleText: StateFlow<String> =
        wordBookInfo
            .map { info -> formatWordBookTitleText(info?.title) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WORD_BOOK_INFO_PLACEHOLDER)

    val wordBookLearningWordsText: StateFlow<String> =
        wordBookInfo
            .map { info -> formatWordBookNumberText(info?.learningWords) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WORD_BOOK_INFO_PLACEHOLDER)

    val wordBookMasteredWordsText: StateFlow<String> =
        wordBookInfo
            .map { info -> formatWordBookNumberText(info?.masteredWords) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WORD_BOOK_INFO_PLACEHOLDER)

    val wordBookTotalWordsText: StateFlow<String> =
        wordBookInfo
            .map { info -> formatWordBookNumberText(info?.totalWords) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WORD_BOOK_INFO_PLACEHOLDER)

    val wordBookRemainWordsText: StateFlow<String> =
        wordBookInfo
            .map { info -> formatWordBookNumberText(info?.remainWords) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WORD_BOOK_INFO_PLACEHOLDER)

    val wordBookStudyDayCountText: StateFlow<String> =
        wordBookInfo
            .map { info -> formatWordBookNumberText(info?.studyDayCount) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WORD_BOOK_INFO_PLACEHOLDER)

    val wordBookAccuracyRateText: StateFlow<String> =
        wordBookInfo
            .map { info -> formatWordBookAccuracyRateText(info?.accuracyRate) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WORD_BOOK_INFO_PLACEHOLDER)

    val wordBookRemainDaysText: StateFlow<String> =
        combine(wordBookInfo, studyPlan) { info, plan ->
            formatWordBookRemainDaysText(info, plan)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WORD_BOOK_INFO_PLACEHOLDER)

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
        viewModelScope.launch {
            syncFacade.refreshHomeData()
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
        val todayNew = todayNewCount.value
        val completedNewPlan = shouldShowBoostNewWordsDialog(todayNew, plan)
        if (!completedNewPlan) {
            val progress = resolveNewLearningProgress(todayNew, plan)
            pendingBoostBookId = null
            pendingBoostPlan = null
            navigateToNewLearning(
                bookId = bookId,
                plan = plan,
                count = progress.remainingCount,
                initialLearnedCount = progress.initialLearnedCount
            )
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

    private fun navigateToNewLearning(
        bookId: Long,
        plan: StudyPlan,
        count: Int,
        initialLearnedCount: Int = 0
    ) {
        viewModelScope.launch {
            val route = learningLauncher.createNewRoute(
                bookId = bookId,
                count = count,
                orderType = plan.wordOrderType,
                initialLearnedCount = initialLearnedCount
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

internal data class NewLearningProgress(
    val remainingCount: Int,
    val initialLearnedCount: Int
)

internal fun shouldShowBoostNewWordsDialog(todayNewCount: Int, plan: StudyPlan): Boolean {
    return plan.dailyNewCount > 0 && todayNewCount >= plan.dailyNewCount
}

internal fun resolveNewLearningProgress(
    todayNewCount: Int,
    plan: StudyPlan
): NewLearningProgress {
    val dailyNewCount = plan.dailyNewCount.coerceAtLeast(0)
    val initialLearnedCount = todayNewCount.coerceAtLeast(0).coerceAtMost(dailyNewCount)
    return NewLearningProgress(
        remainingCount = (dailyNewCount - initialLearnedCount).coerceAtLeast(0),
        initialLearnedCount = initialLearnedCount
    )
}

internal fun createLearningRoute(
    request: DomainLearningSessionRequest?
): AppRoute.Learning? {
    val safeRequest = request ?: return null
    if (safeRequest.wordIds.isEmpty()) return null
    return AppRoute.Learning(
        initialLearnedCount = safeRequest.initialLearnedCount,
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
        initialLearnedCount = 0,
        wordIds = learningWords.map { it.id },
        sessionType = sessionType,
        sessionWordCount = sessionWordCount
    )
}

internal object LearningSessionTypeContract {
    const val NEW: Int = 0
    const val REVIEW: Int = 1
}

internal fun formatWordBookTitleText(title: String?): String {
    return title?.takeIf { it.isNotBlank() } ?: WORD_BOOK_INFO_PLACEHOLDER
}

internal fun formatWordBookNumberText(value: Int?): String {
    return value?.toString() ?: WORD_BOOK_INFO_PLACEHOLDER
}

internal fun formatWordBookAccuracyRateText(value: Float?): String {
    return value?.let { String.format("%.1f%%", it) } ?: WORD_BOOK_INFO_PLACEHOLDER
}

internal fun formatWordBookRemainDaysText(
    info: WordBookInfo?,
    plan: StudyPlan
): String {
    val safeDailyNewCount = plan.dailyNewCount.takeIf { it > 0 } ?: 1
    return info?.remainDays(safeDailyNewCount)?.toString() ?: WORD_BOOK_INFO_PLACEHOLDER
}

internal fun canOpenPendingSyncDetails(state: SyncBannerState): Boolean {
    return state != SyncBannerState.Hidden
}
