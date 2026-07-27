package com.chen.memorizewords.feature.home.ui.stats

import androidx.lifecycle.viewModelScope
import com.chen.memorizewords.core.common.resource.ResourceProvider
import com.chen.memorizewords.core.ui.vm.BaseViewModel
import com.chen.memorizewords.domain.study.model.record.DailyDurationStats
import com.chen.memorizewords.domain.study.model.record.DailyWordStats
import com.chen.memorizewords.domain.study.usecase.word.study.GetCurrentBusinessDateUseCase
import com.chen.memorizewords.domain.study.usecase.word.study.GetContinuousCheckInDaysUseCase
import com.chen.memorizewords.domain.study.usecase.word.study.GetMonthCalendarStatsUseCase
import com.chen.memorizewords.domain.study.usecase.word.study.GetStudyTotalDayCountUseCase
import com.chen.memorizewords.domain.study.usecase.word.study.GetStudyTotalDurationUseCase
import com.chen.memorizewords.domain.study.usecase.word.study.GetStudyTotalWordCountUseCase
import com.chen.memorizewords.domain.study.usecase.word.study.GetTodayNewWordCountUseCase
import com.chen.memorizewords.domain.study.usecase.word.study.GetTodayReviewWordCountUseCase
import com.chen.memorizewords.domain.study.usecase.word.study.GetTodayStudyDurationUseCase
import com.chen.memorizewords.domain.study.usecase.word.study.GetWeeklyDurationStatsUseCase
import com.chen.memorizewords.domain.study.usecase.word.study.GetWeeklyWordStatsUseCase
import com.chen.memorizewords.domain.wordbook.model.WordBookInfo
import com.chen.memorizewords.domain.wordbook.usecase.GetCurrentWordBookInfoFlowUseCase
import com.chen.memorizewords.feature.home.R
import com.chen.memorizewords.feature.home.ui.practice.PracticeLevelUi
import com.chen.memorizewords.feature.home.ui.practice.PracticeUiMapper
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Calendar
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class StatsViewModel @Inject constructor(
    getStudyTotalDayCountUseCase: GetStudyTotalDayCountUseCase,
    getStudyTotalDurationUseCase: GetStudyTotalDurationUseCase,
    getStudyTotalWordCountUseCase: GetStudyTotalWordCountUseCase,
    getContinuousCheckInDaysUseCase: GetContinuousCheckInDaysUseCase,
    getTodayNewWordCountUseCase: GetTodayNewWordCountUseCase,
    getTodayReviewWordCountUseCase: GetTodayReviewWordCountUseCase,
    getTodayStudyDurationUseCase: GetTodayStudyDurationUseCase,
    private val getCurrentBusinessDateUseCase: GetCurrentBusinessDateUseCase,
    private val getWeeklyWordStatsUseCase: GetWeeklyWordStatsUseCase,
    private val getWeeklyDurationStatsUseCase: GetWeeklyDurationStatsUseCase,
    private val getMonthCalendarStatsUseCase: GetMonthCalendarStatsUseCase,
    getCurrentWordBookInfoFlowUseCase: GetCurrentWordBookInfoFlowUseCase,
    private val practiceUiMapper: PracticeUiMapper,
    private val resourceProvider: ResourceProvider
) : BaseViewModel() {

    private val dateCalculator = StatsDateCalculator()
    private val formatter = StatsFormatter(resourceProvider)
    private val calendarBuilder = StatsCalendarBuilder(dateCalculator, formatter)

    private val currentBusinessDate: StateFlow<String> =
        flow {
            while (true) {
                emit(getCurrentBusinessDateUseCase())
                delay(CURRENT_BUSINESS_DATE_REFRESH_INTERVAL_MS)
            }
        }
            .distinctUntilChanged()
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                getCurrentBusinessDateUseCase()
            )

    private val _displayMonth = MutableStateFlow(dateCalculator.currentMonthStart(currentBusinessDate.value))
    private val _selectedDate = MutableStateFlow(currentBusinessDate.value)
    private val _wordFilter = MutableStateFlow(WeeklyWordFilter.ALL)
    val wordFilter: StateFlow<WeeklyWordFilter> = _wordFilter.asStateFlow()

    private val weekRange: StateFlow<StatsWeekRange> =
        currentBusinessDate
            .map(dateCalculator::buildCurrentWeekRange)
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                dateCalculator.buildCurrentWeekRange(currentBusinessDate.value)
            )

    val totalStudyDaysText: StateFlow<String> =
        getStudyTotalDayCountUseCase()
            .map { it.toString() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "0")

    private val totalStudyDays: StateFlow<Int> =
        getStudyTotalDayCountUseCase()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    private val continuousCheckInDays: StateFlow<Int> =
        getContinuousCheckInDaysUseCase()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val continuousCheckInDaysText: StateFlow<String> =
        continuousCheckInDays
            .map { it.toString() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "0")

    private val todayNewWordCount: StateFlow<Int> =
        getTodayNewWordCountUseCase()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    private val todayReviewWordCount: StateFlow<Int> =
        getTodayReviewWordCountUseCase()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    private val todayStudyDurationMs: StateFlow<Long> =
        getTodayStudyDurationUseCase()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    private val todayWordCount: StateFlow<Int> =
        combine(todayNewWordCount, todayReviewWordCount) { newCount, reviewCount ->
            newCount + reviewCount
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    private val currentWordBookInfo: StateFlow<WordBookInfo?> =
        getCurrentWordBookInfoFlowUseCase()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val totalStudyDurationText: StateFlow<String> =
        getStudyTotalDurationUseCase()
            .map { formatHoursValue(it) }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                "0"
            )

    private val totalStudyDurationMs: StateFlow<Long> =
        getStudyTotalDurationUseCase()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    val levelUi: StateFlow<PracticeLevelUi> =
        totalStudyDurationMs
            .map(practiceUiMapper::buildPracticeLevel)
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                practiceUiMapper.buildPracticeLevel(0L)
            )

    val totalStudyWordsText: StateFlow<String> =
        getStudyTotalWordCountUseCase()
            .map { it.toString() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "0")

    private val totalStudyWords: StateFlow<Int> =
        getStudyTotalWordCountUseCase()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val monthTitle: StateFlow<String> =
        _displayMonth
            .map { dateCalculator.formatMonthTitle(it.time) }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                dateCalculator.formatMonthTitle(Calendar.getInstance().time)
            )

    val canGoNextMonth: StateFlow<Boolean> =
        combine(_displayMonth, currentBusinessDate) { month, businessDate ->
            dateCalculator.canMoveToNextMonth(month, businessDate)
        }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val showBackToday: StateFlow<Boolean> =
        combine(_displayMonth, currentBusinessDate) { month, businessDate ->
            !dateCalculator.isSameMonth(month, dateCalculator.currentMonthStart(businessDate))
        }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val weekWordStats: StateFlow<List<DailyWordStats>> =
        weekRange
            .flatMapLatest { range ->
                getWeeklyWordStatsUseCase(range.startDate, range.endDate)
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val weekDurationStats: StateFlow<List<DailyDurationStats>> =
        weekRange
            .flatMapLatest { range ->
                getWeeklyDurationStatsUseCase(range.startDate, range.endDate)
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val weekWordBars: StateFlow<List<WeekBarUi>> =
        combine(weekRange, weekWordStats, _wordFilter) { range, stats, filter ->
            calendarBuilder.buildWeekWordBars(range, stats, filter)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val weekDurationBars: StateFlow<List<WeekBarUi>> =
        combine(weekRange, weekDurationStats) { range, stats ->
            calendarBuilder.buildWeekDurationBars(range, stats)
        }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val overviewCards: StateFlow<List<StatsOverviewCardUi>> =
        currentWordBookInfo.map { info ->
            buildOverviewCards(
                learnedWords = info?.learnedWords ?: 0,
                learningWords = info?.learningWords ?: 0,
                masteredWords = info?.masteredWords ?: 0,
                accuracyRate = info?.accuracyRate ?: 0f
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val trendPoints: StateFlow<List<StatsTrendPointUi>> =
        combine(weekRange, weekWordStats, weekDurationStats) { range, wordStats, durationStats ->
            buildTrendPoints(range, wordStats, durationStats, formatter.weekLabels())
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val timeDistribution: StateFlow<List<StatsTimeDistributionUi>> =
        weekDurationStats
            .map { buildTimeDistribution(it) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), buildTimeDistribution(emptyList()))

    val achievements: StateFlow<List<StatsAchievementUi>> =
        combine(
            continuousCheckInDays,
            totalStudyWords,
            totalStudyDurationMs,
            currentWordBookInfo
        ) { streakDays, words, durationMs, wordBookInfo ->
            buildAchievements(streakDays, words, durationMs, wordBookInfo)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val reportRows: StateFlow<List<StatsReportRowUi>> =
        combine(
            weekRange,
            weekWordStats,
            weekDurationStats,
            continuousCheckInDays
        ) { range, wordStats, durationStats, streakDays ->
            buildReportRows(range, wordStats, durationStats, streakDays, formatter.weekLabels())
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val rawCalendarPagerPages: StateFlow<List<CalendarMonthPageUi>> =
        combine(_displayMonth, currentBusinessDate) { month, businessDate ->
            month to businessDate
        }
            .flatMapLatest { (month, businessDate) ->
                val previousMonth = dateCalculator.shiftMonth(month, -1)
                val nextMonth = dateCalculator.shiftMonth(month, 1)
                val start = dateCalculator.buildCalendarGridRange(previousMonth).startDate
                val end = dateCalculator.buildCalendarGridRange(nextMonth).endDate
                getMonthCalendarStatsUseCase(start, end)
                    .map { stats ->
                        calendarBuilder.buildCalendarPagerPages(
                            anchorMonth = month,
                            stats = stats,
                            currentBusinessDate = businessDate
                        )
                    }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val calendarPagerPages: StateFlow<List<CalendarMonthPageUi>> =
        combine(rawCalendarPagerPages, _selectedDate) { pages, selectedDate ->
            pages.map { page ->
                page.copy(
                    cells = page.cells.map { cell ->
                        cell.copy(isSelected = selectedDate.isNotBlank() && cell.date == selectedDate)
                    }
                )
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            var previousBusinessDate: String? = null
            currentBusinessDate.collect { businessDate ->
                val oldBusinessDate = previousBusinessDate
                previousBusinessDate = businessDate
                if (oldBusinessDate == null) {
                    return@collect
                }
                if (shouldFollowBusinessDateSelection(_selectedDate.value, oldBusinessDate)) {
                    _selectedDate.value = businessDate
                }
                if (shouldFollowBusinessMonth(_displayMonth.value, oldBusinessDate)) {
                    _displayMonth.value = dateCalculator.currentMonthStart(businessDate)
                }
            }
        }
    }

    fun shiftToPreviousMonth() {
        shiftDisplayMonth(-1)
    }

    fun shiftToNextMonth(): Boolean {
        val current = _displayMonth.value
        if (!dateCalculator.canMoveToNextMonth(current, currentBusinessDate.value)) {
            return false
        }
        shiftDisplayMonth(1)
        return true
    }

    fun backToToday() {
        _displayMonth.value = dateCalculator.currentMonthStart(currentBusinessDate.value)
        _selectedDate.value = currentBusinessDate.value
    }

    fun selectDate(date: String) {
        _selectedDate.value = date
    }

    fun onUnavailableFeatureClicked() {
        showToast(resourceProvider.getString(R.string.feature_home_stats_unavailable))
    }

    fun selectAllWords() {
        _wordFilter.value = WeeklyWordFilter.ALL
    }

    fun selectNewWords() {
        _wordFilter.value = WeeklyWordFilter.NEW
    }

    fun selectReviewWords() {
        _wordFilter.value = WeeklyWordFilter.REVIEW
    }

    private fun shiftDisplayMonth(delta: Int) {
        _displayMonth.update { dateCalculator.shiftMonth(it, delta) }
        val newMonth = _displayMonth.value
        val selected = _selectedDate.value
        if (selected.isNotBlank() && !dateCalculator.isDateInMonth(selected, newMonth)) {
            _selectedDate.value = ""
        }
    }

    private companion object {
        private const val CURRENT_BUSINESS_DATE_REFRESH_INTERVAL_MS = 60_000L
    }
}

internal fun buildOverviewCards(
    learnedWords: Int,
    learningWords: Int,
    masteredWords: Int,
    accuracyRate: Float = 0f
): List<StatsOverviewCardUi> {
    return listOf(
        StatsOverviewCardUi(
            value = learnedWords.coerceAtLeast(0).toString(),
            unit = "词",
            title = "已学习",
            changeText = "学习中 ${learningWords.coerceAtLeast(0)} · 已掌握 ${masteredWords.coerceAtLeast(0)}",
            iconResId = R.drawable.feature_home_ic_profile_book_open,
            iconBackgroundResId = R.drawable.feature_home_stats_icon_green_bg
        ),
        StatsOverviewCardUi(
            value = learningWords.coerceAtLeast(0).toString(),
            unit = "词",
            title = "学习中",
            changeText = "当前词书",
            iconResId = R.drawable.feature_home_ic_profile_flame,
            iconBackgroundResId = R.drawable.feature_home_stats_icon_orange_bg
        ),
        StatsOverviewCardUi(
            value = masteredWords.coerceAtLeast(0).toString(),
            unit = "词",
            title = "已掌握",
            changeText = "当前词书",
            iconResId = R.drawable.feature_home_stats_ic_check_circle,
            iconBackgroundResId = R.drawable.feature_home_stats_icon_blue_bg
        ),
        StatsOverviewCardUi(
            value = formatPercentValue(accuracyRate),
            unit = "%",
            title = "答题正确率",
            changeText = "当前词书累计",
            iconResId = R.drawable.feature_home_ic_profile_menu_chart,
            iconBackgroundResId = R.drawable.feature_home_stats_icon_purple_bg
        )
    )
}

internal fun buildTrendPoints(
    weekRange: StatsWeekRange,
    wordStats: List<DailyWordStats>,
    durationStats: List<DailyDurationStats>,
    weekLabels: List<String>
): List<StatsTrendPointUi> {
    val wordMap = wordStats.associateBy { it.date }
    val durationMap = durationStats.associateBy { it.date }
    return weekRange.dates.mapIndexed { index, date ->
        StatsTrendPointUi(
            dayLabel = weekLabels.getOrElse(index) { "" },
            durationHours = ((durationMap[date]?.durationMs ?: 0L) / 3_600_000f).coerceAtLeast(0f),
            newWordCount = (wordMap[date]?.newCount ?: 0).coerceAtLeast(0)
        )
    }
}

internal fun buildTimeDistribution(_stats: List<DailyDurationStats>): List<StatsTimeDistributionUi> {
    return emptyList()
}

internal fun buildAchievements(
    streakDays: Int,
    totalWords: Int,
    totalDurationMs: Long,
    currentWordBookInfo: WordBookInfo? = null
): List<StatsAchievementUi> {
    val safeStreakDays = streakDays.coerceAtLeast(0)
    val safeTotalWords = totalWords.coerceAtLeast(0)
    val safeDurationMs = totalDurationMs.coerceAtLeast(0L)
    val completedCurrentBook = currentWordBookInfo?.let { info ->
        info.totalWords > 0 && info.masteredWords >= info.totalWords
    } == true
    val remainingHours = remainingWholeUnits(
        current = safeDurationMs,
        target = ACHIEVEMENT_DURATION_TARGET_MS,
        unit = HOUR_MS
    )
    val remainingWords = (ACHIEVEMENT_WORD_TARGET - safeTotalWords).coerceAtLeast(0)
    return listOf(
        StatsAchievementUi(
            title = "连续学习 $safeStreakDays 天",
            subtitle = if (safeStreakDays >= 1) "已解锁" else "再学习 1 天解锁",
            iconResId = R.drawable.feature_home_ic_achievement_streak,
            achieved = safeStreakDays >= 1
        ),
        StatsAchievementUi(
            title = "完成首个词书",
            subtitle = if (completedCurrentBook) "已解锁" else "继续加油",
            iconResId = R.drawable.feature_home_ic_achievement_book,
            achieved = completedCurrentBook
        ),
        StatsAchievementUi(
            title = "累计学习 10 小时",
            subtitle = if (remainingHours == 0L) "已解锁" else "还差 $remainingHours 小时",
            iconResId = R.drawable.feature_home_ic_achievement_clock,
            achieved = remainingHours == 0L
        ),
        StatsAchievementUi(
            title = "新词突破 50 个",
            subtitle = if (remainingWords == 0) "已解锁" else "还差 $remainingWords 个",
            iconResId = R.drawable.feature_home_ic_achievement_star,
            achieved = remainingWords == 0
        )
    )
}

internal fun buildReportRows(
    weekRange: StatsWeekRange,
    wordStats: List<DailyWordStats>,
    durationStats: List<DailyDurationStats>,
    streakDays: Int,
    weekLabels: List<String>
): List<StatsReportRowUi> {
    val totalDurationMs = durationStats.sumOf { it.durationMs }
    val activeDates = buildSet {
        wordStats
            .filter { it.newCount > 0 || it.reviewCount > 0 }
            .mapTo(this) { it.date }
        durationStats
            .filter { it.durationMs > 0L }
            .mapTo(this) { it.date }
    }
    val bestDuration = durationStats.maxByOrNull { it.durationMs }
    val bestDayIndex = bestDuration
        ?.takeIf { it.durationMs > 0L }
        ?.let { weekRange.dates.indexOf(it.date) }
        ?: -1
    val bestDay = if (bestDayIndex >= 0) {
        weekLabels.getOrElse(bestDayIndex) { "--" }
    } else {
        "--"
    }
    return listOf(
        StatsReportRowUi("本周学习时长", formatHoursValue(totalDurationMs), "小时", R.drawable.feature_home_ic_profile_clock, R.drawable.feature_home_stats_icon_blue_bg),
        StatsReportRowUi("本周学习次数", activeDates.size.toString(), "次", R.drawable.feature_home_ic_profile_book_open, R.drawable.feature_home_stats_icon_green_bg),
        StatsReportRowUi("最佳学习日", bestDay, "", R.drawable.feature_home_ic_profile_report_calendar_star, R.drawable.feature_home_stats_icon_green_bg),
        StatsReportRowUi("连续打卡天数", streakDays.coerceAtLeast(0).toString(), "天", R.drawable.feature_home_ic_profile_flame, R.drawable.feature_home_stats_icon_orange_bg)
    )
}

private fun remainingWholeUnits(current: Long, target: Long, unit: Long): Long {
    val remaining = (target - current).coerceAtLeast(0L)
    return if (remaining == 0L) 0L else (remaining + unit - 1L) / unit
}

internal fun formatHoursValue(durationMs: Long): String {
    val hours = durationMs.coerceAtLeast(0L) / 3_600_000.0
    val rounded = kotlin.math.round(hours * 10.0) / 10.0
    return if (rounded % 1.0 == 0.0) {
        rounded.toInt().toString()
    } else {
        String.format(java.util.Locale.US, "%.1f", rounded)
    }
}

internal fun formatPercentValue(value: Float): String {
    val rounded = kotlin.math.round(value.coerceAtLeast(0f) * 10f) / 10f
    return String.format(java.util.Locale.US, "%.1f", rounded)
}

internal fun shouldFollowBusinessDateSelection(
    selectedDate: String,
    previousCurrentBusinessDate: String
): Boolean {
    return selectedDate == previousCurrentBusinessDate
}

internal fun shouldFollowBusinessMonth(
    displayMonth: Calendar,
    previousCurrentBusinessDate: String
): Boolean {
    return displayMonth.get(Calendar.YEAR) == previousCurrentBusinessDate.take(4).toInt() &&
        displayMonth.get(Calendar.MONTH) == previousCurrentBusinessDate.substring(5, 7).toInt() - 1
}

private const val HOUR_MS = 3_600_000L
private const val ACHIEVEMENT_DURATION_TARGET_MS = 10L * HOUR_MS
private const val ACHIEVEMENT_WORD_TARGET = 50
