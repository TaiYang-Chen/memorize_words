package com.chen.memorizewords.feature.home.ui.home

import android.text.Spanned
import androidx.lifecycle.viewModelScope
import com.chen.memorizewords.core.common.resource.ResourceProvider
import com.chen.memorizewords.core.navigation.AppRoute
import com.chen.memorizewords.core.ui.vm.BaseViewModel
import com.chen.memorizewords.core.ui.vm.UiEvent
import com.chen.memorizewords.domain.account.model.user.User
import com.chen.memorizewords.domain.account.usecase.user.GetCurrentUserUseCase
import com.chen.memorizewords.domain.study.model.learning.LearningSessionRequest as DomainLearningSessionRequest
import com.chen.memorizewords.domain.study.orchestrator.learning.LearningSessionFacade
import com.chen.memorizewords.domain.study.service.StudyStatsFacade
import com.chen.memorizewords.domain.sync.model.SyncBannerState
import com.chen.memorizewords.domain.sync.service.SyncFacade
import com.chen.memorizewords.domain.word.model.word.Word
import com.chen.memorizewords.domain.wordbook.model.WordBookInfo
import com.chen.memorizewords.domain.wordbook.model.study.StudyPlan
import com.chen.memorizewords.domain.wordbook.usecase.GetCurrentWordBookInfoFlowUseCase
import com.chen.memorizewords.domain.wordbook.usecase.GetStudyPlanFlowUseCase
import com.chen.memorizewords.feature.home.R
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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
        data object ToStatsTab : Route
        data object ToPracticeTab : Route
        data object ToProfileTab : Route
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

    private val continuousCheckInDays: StateFlow<Int> =
        studyStatsFacade.getContinuousCheckInDays()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val todayNewCount: StateFlow<Int> =
        studyStatsFacade.getTodayNewWordCount()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val todayReviewCount: StateFlow<Int> =
        studyStatsFacade.getTodayReviewWordCount()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    private val todayStudyDurationMs: StateFlow<Long> =
        studyStatsFacade.getTodayStudyDurationMs()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    val dashboardUiState: StateFlow<HomeDashboardUiState> =
        combine(
            wordBookInfo,
            studyPlan,
            todayNewCount,
            todayReviewCount,
            todayStudyDurationMs,
            continuousCheckInDays,
            studyTotalDayCount
        ) { values ->
            buildHomeDashboardUiState(
                wordBookInfo = values[0] as WordBookInfo?,
                plan = values[1] as StudyPlan,
                todayNewCount = values[2] as Int,
                todayReviewCount = values[3] as Int,
                todayStudyDurationMs = values[4] as Long,
                continuousDays = values[5] as Int,
                totalStudyDays = values[6] as Int
            )
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            buildHomeDashboardUiState(
                wordBookInfo = null,
                plan = StudyPlan(),
                todayNewCount = 0,
                todayReviewCount = 0,
                todayStudyDurationMs = 0L,
                continuousDays = 0,
                totalStudyDays = 0
            )
        )

    val homeTitleText: StateFlow<String> = dashboardField { it.homeTitleText }
    val wordBookTitleText: StateFlow<String> = dashboardField { it.wordBookTitleText }
    val wordBookLearningWordsText: StateFlow<String> =
        wordBookInfo
            .map { info -> formatWordBookNumberText(info?.learningWords) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WORD_BOOK_INFO_PLACEHOLDER)
    val wordBookMasteredWordsText: StateFlow<String> = masteredWordsTextCompat()
    val wordBookTotalWordsText: StateFlow<String> = totalWordsTextCompat()
    val wordBookRemainWordsText: StateFlow<String> =
        wordBookInfo
            .map { info -> formatWordBookNumberText(info?.remainWords) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WORD_BOOK_INFO_PLACEHOLDER)
    val wordBookStudyDayCountText: StateFlow<String> =
        wordBookInfo
            .map { info -> formatWordBookNumberText(info?.studyDayCount) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WORD_BOOK_INFO_PLACEHOLDER)
    val wordBookAccuracyRateText: StateFlow<String> = accuracyRateTextCompat()
    val wordBookRemainDaysText: StateFlow<String> = expectedCompletionTextCompat()
    val streakText: StateFlow<String> = dashboardField { it.streakText }
    val progressPercentText: StateFlow<String> = dashboardField { it.progressPercentText }
    val todayCompletedWordsText: StateFlow<String> = dashboardField { it.todayCompletedWordsText }
    val remainingReviewWordsText: StateFlow<String> = dashboardField { it.remainingReviewWordsText }
    val learnButtonSubtitleText: StateFlow<String> = dashboardField { it.learnButtonSubtitleText }
    val reviewCardSubtitleText: StateFlow<String> = dashboardField { it.reviewCardSubtitleText }
    val planCardSubtitleText: StateFlow<String> = dashboardField { it.planCardSubtitleText }
    val todayNewProgressText: StateFlow<String> = dashboardField { it.todayNewProgressText }
    val todayReviewProgressText: StateFlow<String> = dashboardField { it.todayReviewProgressText }
    val todayNewStatusText: StateFlow<String> = dashboardField { it.todayNewStatusText }
    val todayReviewStatusText: StateFlow<String> = dashboardField { it.todayReviewStatusText }
    val estimatedStudyMinutesText: StateFlow<String> = dashboardField { it.estimatedStudyMinutesText }
    val masteredWordsText: StateFlow<String> = dashboardField { it.masteredWordsText }
    val totalWordsText: StateFlow<String> = dashboardField { it.totalWordsText }
    val accuracyRateText: StateFlow<String> = dashboardField { it.accuracyRateText }
    val continuousDaysText: StateFlow<String> = dashboardField { it.continuousDaysText }
    val expectedCompletionText: StateFlow<String> = dashboardField { it.expectedCompletionText }
    val todayStudyDurationText: StateFlow<String> = dashboardField { it.todayStudyDurationText }
    val totalStudyDaysText: StateFlow<String> = dashboardField { it.totalStudyDaysText }
    val wordBookHeroContentDescription: StateFlow<String> = dashboardField { it.wordBookHeroContentDescription }
    val reviewCardTitleText: StateFlow<String> = dashboardField { it.reviewCardTitleText }
    val reviewCardDescriptionText: StateFlow<String> = dashboardField { it.reviewCardDescriptionText }
    val planCardTitleText: StateFlow<String> = dashboardField { it.planCardTitleText }
    val planCardDescriptionText: StateFlow<String> = dashboardField { it.planCardDescriptionText }
    val todaySectionTitleText: StateFlow<String> = dashboardField { it.todaySectionTitleText }
    val overviewSectionTitleText: StateFlow<String> = dashboardField { it.overviewSectionTitleText }
    val newTaskTitleText: StateFlow<String> = dashboardField { it.newTaskTitleText }
    val reviewTaskTitleText: StateFlow<String> = dashboardField { it.reviewTaskTitleText }
    val estimatedTaskTitleText: StateFlow<String> = dashboardField { it.estimatedTaskTitleText }
    val estimatedTaskHintText: StateFlow<String> = dashboardField { it.estimatedTaskHintText }
    val masteredWordsTitleText: StateFlow<String> = dashboardField { it.masteredWordsTitleText }
    val totalWordsTitleText: StateFlow<String> = dashboardField { it.totalWordsTitleText }
    val accuracyRateTitleText: StateFlow<String> = dashboardField { it.accuracyRateTitleText }
    val continuousDaysTitleText: StateFlow<String> = dashboardField { it.continuousDaysTitleText }
    val expectedCompletionTitleText: StateFlow<String> = dashboardField { it.expectedCompletionTitleText }
    val todayDurationTitleText: StateFlow<String> = dashboardField { it.todayDurationTitleText }
    val learnProcess: StateFlow<Int> = dashboardField { it.progressPercent }
    val todayNewDoneIconVisible: StateFlow<Boolean> = dashboardField { it.todayNewDone }
    val todayReviewDoneIconVisible: StateFlow<Boolean> = dashboardField { it.todayReviewDone }

    private fun <T> dashboardField(selector: (HomeDashboardUiState) -> T): StateFlow<T> {
        return dashboardUiState
            .map(selector)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), selector(dashboardUiState.value))
    }

    private fun masteredWordsTextCompat(): StateFlow<String> =
        dashboardField { it.masteredWordsText }

    private fun totalWordsTextCompat(): StateFlow<String> =
        dashboardField { it.totalWordsText }

    private fun accuracyRateTextCompat(): StateFlow<String> =
        dashboardField { it.accuracyRateText }

    private fun expectedCompletionTextCompat(): StateFlow<String> =
        dashboardField { it.expectedCompletionText }

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

    fun onStatsClicked() {
        navigateRoute(Route.ToStatsTab)
    }

    fun onPracticeClicked() {
        navigateRoute(Route.ToPracticeTab)
    }

    fun onProfileClicked() {
        navigateRoute(Route.ToProfileTab)
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

data class HomeDashboardUiState(
    val homeTitleText: String,
    val wordBookTitleText: String,
    val streakText: String,
    val progressPercent: Int,
    val progressPercentText: String,
    val todayCompletedWordsText: String,
    val remainingReviewWordsText: String,
    val learnButtonSubtitleText: String,
    val reviewCardSubtitleText: String,
    val planCardSubtitleText: String,
    val todayNewProgressText: String,
    val todayReviewProgressText: String,
    val todayNewStatusText: String,
    val todayReviewStatusText: String,
    val todayNewDone: Boolean,
    val todayReviewDone: Boolean,
    val estimatedStudyMinutesText: String,
    val masteredWordsText: String,
    val totalWordsText: String,
    val accuracyRateText: String,
    val continuousDaysText: String,
    val expectedCompletionText: String,
    val todayStudyDurationText: String,
    val totalStudyDaysText: String,
    val wordBookHeroContentDescription: String,
    val reviewCardTitleText: String,
    val reviewCardDescriptionText: String,
    val planCardTitleText: String,
    val planCardDescriptionText: String,
    val todaySectionTitleText: String,
    val overviewSectionTitleText: String,
    val newTaskTitleText: String,
    val reviewTaskTitleText: String,
    val estimatedTaskTitleText: String,
    val estimatedTaskHintText: String,
    val masteredWordsTitleText: String,
    val totalWordsTitleText: String,
    val accuracyRateTitleText: String,
    val continuousDaysTitleText: String,
    val expectedCompletionTitleText: String,
    val todayDurationTitleText: String
)

internal fun buildHomeDashboardUiState(
    wordBookInfo: WordBookInfo?,
    plan: StudyPlan,
    todayNewCount: Int,
    todayReviewCount: Int,
    todayStudyDurationMs: Long,
    continuousDays: Int,
    totalStudyDays: Int
): HomeDashboardUiState {
    val safeNewPlan = plan.dailyNewCount.coerceAtLeast(0)
    val safeReviewPlan = plan.dailyReviewCount.coerceAtLeast(0)
    val safeTodayNew = todayNewCount.coerceAtLeast(0)
    val safeTodayReview = todayReviewCount.coerceAtLeast(0)
    val estimatedMinutes = calculateEstimatedStudyMinutes(plan)

    return HomeDashboardUiState(
        homeTitleText = "\u5c0f\u767d\u80cc\u5355\u8bcd",
        wordBookTitleText = formatWordBookTitleText(wordBookInfo?.title),
        streakText = "\u5df2\u8fde\u7eed\u5b66\u4e60 ${continuousDays.coerceAtLeast(0)} \u5929",
        progressPercent = calculateLearnProgress(safeTodayNew, safeTodayReview, plan),
        progressPercentText = "${calculateLearnProgress(safeTodayNew, safeTodayReview, plan)}%",
        todayCompletedWordsText = "\u4eca\u65e5\u5b8c\u6210 ${formatTodayCompletedWordsText(safeTodayNew, safeTodayReview, plan)} \u4e2a\u5355\u8bcd",
        remainingReviewWordsText = "\u8fd8\u9700\u590d\u4e60 ${calculateRemainingCount(safeTodayReview, safeReviewPlan)} \u4e2a\u5355\u8bcd",
        learnButtonSubtitleText = "\u65b0\u5b66 $safeNewPlan \u4e2a\u5355\u8bcd \u00b7 \u9884\u8ba1 $estimatedMinutes \u5206\u949f",
        reviewCardSubtitleText = "${formatCountProgressText(safeTodayReview, safeReviewPlan)} ${formatTaskStatus(safeTodayReview, safeReviewPlan)}",
        planCardSubtitleText = "\u65b0\u5b66 $safeNewPlan / \u590d\u4e60 $safeReviewPlan",
        todayNewProgressText = formatCountProgressText(safeTodayNew, safeNewPlan),
        todayReviewProgressText = formatCountProgressText(safeTodayReview, safeReviewPlan),
        todayNewStatusText = formatTaskStatus(safeTodayNew, safeNewPlan),
        todayReviewStatusText = formatTaskStatus(safeTodayReview, safeReviewPlan),
        todayNewDone = isTaskDone(safeTodayNew, safeNewPlan),
        todayReviewDone = isTaskDone(safeTodayReview, safeReviewPlan),
        estimatedStudyMinutesText = formatMinuteText(estimatedMinutes),
        masteredWordsText = formatWordBookNumberText(wordBookInfo?.masteredWords),
        totalWordsText = formatWordBookNumberText(wordBookInfo?.totalWords),
        accuracyRateText = formatWordBookAccuracyRateText(wordBookInfo?.accuracyRate),
        continuousDaysText = formatDayCountText(continuousDays),
        expectedCompletionText = formatExpectedCompletionText(wordBookInfo, plan),
        todayStudyDurationText = formatDurationText(todayStudyDurationMs),
        totalStudyDaysText = formatDayCountText(totalStudyDays),
        wordBookHeroContentDescription = "\u8bcd\u4e66\u63d2\u753b",
        reviewCardTitleText = "\u590d\u4e60\u5355\u8bcd",
        reviewCardDescriptionText = "\u5de9\u56fa\u8bb0\u5fc6\uff0c\u5f3a\u5316\u638c\u63e1",
        planCardTitleText = "\u4fee\u6539\u8ba1\u5212",
        planCardDescriptionText = "\u8c03\u6574\u6bcf\u65e5\u5b66\u4e60\u76ee\u6807",
        todaySectionTitleText = "\u4eca\u65e5\u4efb\u52a1",
        overviewSectionTitleText = "\u5b66\u4e60\u6982\u89c8",
        newTaskTitleText = "\u65b0\u8bcd\u5b66\u4e60",
        reviewTaskTitleText = "\u590d\u4e60\u5355\u8bcd",
        estimatedTaskTitleText = "\u9884\u8ba1\u7528\u65f6",
        estimatedTaskHintText = "\u5408\u7406\u5b89\u6392\u65f6\u95f4",
        masteredWordsTitleText = "\u5df2\u638c\u63e1\u5355\u8bcd",
        totalWordsTitleText = "\u603b\u8bcd\u91cf",
        accuracyRateTitleText = "\u6b63\u786e\u7387",
        continuousDaysTitleText = "\u8fde\u7eed\u5b66\u4e60",
        expectedCompletionTitleText = "\u9884\u8ba1\u5b8c\u6210",
        todayDurationTitleText = "\u4eca\u65e5\u7528\u65f6"
    )
}

internal fun calculateLearnProgress(
    todayNewCount: Int,
    todayReviewCount: Int,
    plan: StudyPlan
): Int {
    val safeDailyNew = plan.dailyNewCount.coerceAtLeast(0)
    val safeDailyReview = plan.dailyReviewCount.coerceAtLeast(0)
    val totalPlan = safeDailyNew + safeDailyReview
    if (totalPlan <= 0) {
        return 0
    }
    val boundedNewCount = todayNewCount.coerceAtLeast(0).coerceAtMost(safeDailyNew)
    val boundedReviewCount = todayReviewCount.coerceAtLeast(0).coerceAtMost(safeDailyReview)
    return (((boundedNewCount + boundedReviewCount).toFloat() / totalPlan.toFloat()) * 100).toInt()
}

internal fun calculateTodayPlanTotalCount(plan: StudyPlan): Int {
    return plan.dailyNewCount.coerceAtLeast(0) + plan.dailyReviewCount.coerceAtLeast(0)
}

internal fun calculateRemainingCount(doneCount: Int, planCount: Int): Int {
    return (planCount.coerceAtLeast(0) - doneCount.coerceAtLeast(0)).coerceAtLeast(0)
}

internal fun calculateEstimatedStudyMinutes(plan: StudyPlan): Int {
    return plan.dailyNewCount.coerceAtLeast(0)
}

internal fun formatTodayCompletedWordsText(
    todayNewCount: Int,
    todayReviewCount: Int,
    plan: StudyPlan
): String {
    val completed = todayNewCount.coerceAtLeast(0) + todayReviewCount.coerceAtLeast(0)
    return "$completed/${calculateTodayPlanTotalCount(plan)}"
}

internal fun formatCountProgressText(doneCount: Int, planCount: Int): String {
    return "${doneCount.coerceAtLeast(0)}/${planCount.coerceAtLeast(0)}"
}

internal fun isTaskDone(doneCount: Int, planCount: Int): Boolean {
    return planCount > 0 && doneCount >= planCount
}

internal fun formatTaskStatus(doneCount: Int, planCount: Int): String {
    val safePlan = planCount.coerceAtLeast(0)
    val safeDone = doneCount.coerceAtLeast(0)
    return when {
        safePlan == 0 -> "\u6682\u65e0\u8ba1\u5212"
        safeDone <= 0 -> "\u5f85\u5b8c\u6210"
        safeDone >= safePlan -> "\u5df2\u5b8c\u6210"
        else -> "\u8fdb\u884c\u4e2d"
    }
}

internal fun formatMinuteText(minutes: Int): String {
    return "${minutes.coerceAtLeast(0)}\u5206\u949f"
}

internal fun formatEstimatedStudyMinutesText(plan: StudyPlan): String {
    return formatMinuteText(calculateEstimatedStudyMinutes(plan))
}

internal fun formatDayCountText(days: Int): String {
    return "${days.coerceAtLeast(0)} \u5929"
}

internal fun formatDurationText(durationMs: Long): String {
    val totalSeconds = (durationMs / 1000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    return if (hours > 0L) {
        "${hours}\u5c0f\u65f6${minutes}\u5206\u949f"
    } else {
        "${minutes}\u5206\u949f"
    }
}

internal fun formatExpectedCompletionText(info: WordBookInfo?, plan: StudyPlan): String {
    val wordBookInfo = info ?: return WORD_BOOK_INFO_PLACEHOLDER
    val remainingWords = wordBookInfo.remainWords.coerceAtLeast(0)
    if (remainingWords == 0) {
        return "\u5df2\u5b8c\u6210"
    }
    val dailyNewCount = plan.dailyNewCount.coerceAtLeast(0)
    if (dailyNewCount == 0) {
        return WORD_BOOK_INFO_PLACEHOLDER
    }
    return "${(remainingWords + dailyNewCount - 1) / dailyNewCount} \u5929"
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
    return value?.coerceAtLeast(0)?.toString() ?: WORD_BOOK_INFO_PLACEHOLDER
}

internal fun formatWordBookAccuracyRateText(value: Float?): String {
    return value?.coerceAtLeast(0f)?.let { String.format(Locale.US, "%.1f%%", it) }
        ?: WORD_BOOK_INFO_PLACEHOLDER
}

internal fun formatWordBookRemainDaysText(
    info: WordBookInfo?,
    plan: StudyPlan
): String {
    return formatExpectedCompletionText(info, plan)
}

internal fun canOpenPendingSyncDetails(state: SyncBannerState): Boolean {
    return state != SyncBannerState.Hidden
}
