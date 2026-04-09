package com.chen.memorizewords.feature.home.ui.stats

import com.chen.memorizewords.domain.model.study.record.CalendarDayStats
import com.chen.memorizewords.domain.model.study.record.DailyDurationStats
import com.chen.memorizewords.domain.model.study.record.DailyWordStats
import java.util.Calendar

internal class StatsCalendarBuilder(
    private val dateCalculator: StatsDateCalculator,
    private val formatter: StatsFormatter
) {

    fun buildWeekWordBars(
        weekRange: StatsWeekRange,
        stats: List<DailyWordStats>,
        filter: WeeklyWordFilter
    ): List<WeekBarUi> {
        val statMap = stats.associateBy { it.date }
        val weekLabels = formatter.weekLabels()
        return weekRange.dates.mapIndexed { index, date ->
            val item = statMap[date]
            val value = when (filter) {
                WeeklyWordFilter.ALL -> (item?.newCount ?: 0) + (item?.reviewCount ?: 0)
                WeeklyWordFilter.NEW -> item?.newCount ?: 0
                WeeklyWordFilter.REVIEW -> item?.reviewCount ?: 0
            }
            WeekBarUi(
                dayLabel = weekLabels[index],
                valueLabel = value.toString(),
                value = value.toLong()
            )
        }
    }

    fun buildWeekDurationBars(
        weekRange: StatsWeekRange,
        stats: List<DailyDurationStats>
    ): List<WeekBarUi> {
        val statMap = stats.associateBy { it.date }
        val weekLabels = formatter.weekLabels()
        return weekRange.dates.mapIndexed { index, date ->
            val durationMs = statMap[date]?.durationMs ?: 0L
            WeekBarUi(
                dayLabel = weekLabels[index],
                valueLabel = formatter.formatBarDuration(durationMs),
                value = durationMs
            )
        }
    }

    fun buildCalendarPagerPages(
        anchorMonth: Calendar,
        stats: List<CalendarDayStats>,
        currentBusinessDate: String
    ): List<CalendarMonthPageUi> {
        val statMap = stats.associateBy { it.date }
        val previousMonth = dateCalculator.shiftMonth(anchorMonth, -1)
        val nextMonth = dateCalculator.shiftMonth(anchorMonth, 1)
        return listOf(previousMonth, anchorMonth, nextMonth).map { month ->
            CalendarMonthPageUi(
                monthLabel = dateCalculator.formatMonthTitle(month.time),
                cells = buildCalendarCells(month, statMap, currentBusinessDate)
            )
        }
    }

    private fun buildCalendarCells(
        month: Calendar,
        statsMap: Map<String, CalendarDayStats>,
        currentBusinessDate: String
    ): List<CalendarDayCellUi> {
        val start = dateCalculator.buildGridStart(month)
        val items = mutableListOf<CalendarDayCellUi>()
        repeat(42) {
            val date = dateCalculator.formatDate(start.time)
            val stat = statsMap[date]
            items.add(
                CalendarDayCellUi(
                    date = date,
                    dayText = start.get(Calendar.DAY_OF_MONTH).toString(),
                    isCurrentMonth = start.get(Calendar.MONTH) == month.get(Calendar.MONTH) &&
                        start.get(Calendar.YEAR) == month.get(Calendar.YEAR),
                    isToday = date == currentBusinessDate,
                    isSelected = false,
                    status = resolveCalendarStatus(stat)
                )
            )
            start.add(Calendar.DAY_OF_MONTH, 1)
        }
        return items
    }

    private fun resolveCalendarStatus(stat: CalendarDayStats?): CalendarStudyStatus {
        if (stat == null) return CalendarStudyStatus.NONE
        if (stat.hasStudy) {
            return when {
                stat.isNewPlanCompleted && stat.isReviewPlanCompleted -> CalendarStudyStatus.ALL_DONE
                stat.isNewPlanCompleted -> CalendarStudyStatus.NEW_DONE
                stat.isReviewPlanCompleted -> CalendarStudyStatus.REVIEW_DONE
                else -> CalendarStudyStatus.STUDIED
            }
        }
        return if (stat.hasCheckIn) {
            CalendarStudyStatus.CHECKED_IN
        } else {
            CalendarStudyStatus.NONE
        }
    }
}
