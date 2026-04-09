package com.chen.memorizewords.feature.home.ui.stats

import androidx.lifecycle.viewModelScope
import com.chen.memorizewords.core.common.resource.ResourceProvider
import com.chen.memorizewords.core.ui.vm.BaseViewModel
import com.chen.memorizewords.domain.model.study.record.DailyDurationStats
import com.chen.memorizewords.domain.model.study.record.DailyWordStats
import com.chen.memorizewords.domain.usecase.word.study.GetCurrentBusinessDateUseCase
import com.chen.memorizewords.domain.usecase.word.study.GetMonthCalendarStatsUseCase
import com.chen.memorizewords.domain.usecase.word.study.GetStudyTotalDayCountUseCase
import com.chen.memorizewords.domain.usecase.word.study.GetStudyTotalDurationUseCase
import com.chen.memorizewords.domain.usecase.word.study.GetStudyTotalWordCountUseCase
import com.chen.memorizewords.domain.usecase.word.study.GetWeeklyDurationStatsUseCase
import com.chen.memorizewords.domain.usecase.word.study.GetWeeklyWordStatsUseCase
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
    private val getCurrentBusinessDateUseCase: GetCurrentBusinessDateUseCase,
    private val getWeeklyWordStatsUseCase: GetWeeklyWordStatsUseCase,
    private val getWeeklyDurationStatsUseCase: GetWeeklyDurationStatsUseCase,
    private val getMonthCalendarStatsUseCase: GetMonthCalendarStatsUseCase,
    resourceProvider: ResourceProvider
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

    val totalStudyDurationText: StateFlow<String> =
        getStudyTotalDurationUseCase()
            .map { (it / 60_000L).coerceAtLeast(0L).toString() }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                "0"
            )

    val totalStudyWordsText: StateFlow<String> =
        getStudyTotalWordCountUseCase()
            .map { it.toString() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "0")

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
