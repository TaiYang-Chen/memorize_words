package com.chen.memorizewords.feature.home.ui.stats

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

internal data class StatsWeekRange(
    val startDate: String,
    val endDate: String,
    val dates: List<String>
)

internal data class StatsDateRange(
    val startDate: String,
    val endDate: String
)

internal class StatsDateCalculator {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val monthTitleFormat = SimpleDateFormat("yyyy.MM", Locale.getDefault())
    private val monthKeyFormat = SimpleDateFormat("yyyy-MM", Locale.getDefault())

    fun formatMonthTitle(date: Date): String {
        return synchronized(monthTitleFormat) { monthTitleFormat.format(date) }
    }

    fun currentMonthStart(date: String): Calendar {
        val baseDate = parseDate(date) ?: Date()
        return Calendar.getInstance().apply {
            time = baseDate
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
    }

    fun buildCurrentWeekRange(date: String): StatsWeekRange {
        val monday = Calendar.getInstance().apply {
            time = parseDate(date) ?: Date()
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            val offset = (get(Calendar.DAY_OF_WEEK) + 5) % 7
            add(Calendar.DAY_OF_MONTH, -offset)
        }
        val dates = mutableListOf<String>()
        val cursor = monday.clone() as Calendar
        repeat(7) {
            dates.add(formatDate(cursor.time))
            cursor.add(Calendar.DAY_OF_MONTH, 1)
        }
        val end = (monday.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, 6) }
        return StatsWeekRange(
            startDate = formatDate(monday.time),
            endDate = formatDate(end.time),
            dates = dates
        )
    }

    fun buildCalendarGridRange(month: Calendar): StatsDateRange {
        val start = buildGridStart(month)
        val end = (start.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, 41) }
        return StatsDateRange(
            startDate = formatDate(start.time),
            endDate = formatDate(end.time)
        )
    }

    fun buildGridStart(month: Calendar): Calendar {
        val firstDay = (month.clone() as Calendar).apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val offset = (firstDay.get(Calendar.DAY_OF_WEEK) + 5) % 7
        return firstDay.apply { add(Calendar.DAY_OF_MONTH, -offset) }
    }

    fun formatDate(date: Date): String {
        return synchronized(dateFormat) { dateFormat.format(date) }
    }

    fun shiftMonth(month: Calendar, delta: Int): Calendar {
        return (month.clone() as Calendar).apply { add(Calendar.MONTH, delta) }
    }

    fun canMoveToNextMonth(month: Calendar, currentBusinessDate: String): Boolean {
        return toYearMonthInt(month) < toYearMonthInt(currentMonthStart(currentBusinessDate))
    }

    fun isSameMonth(first: Calendar, second: Calendar): Boolean {
        return first.get(Calendar.YEAR) == second.get(Calendar.YEAR) &&
            first.get(Calendar.MONTH) == second.get(Calendar.MONTH)
    }

    fun isDateInMonth(date: String, month: Calendar): Boolean {
        val monthKey = synchronized(monthKeyFormat) { monthKeyFormat.format(month.time) }
        return date.startsWith(monthKey)
    }

    private fun toYearMonthInt(month: Calendar): Int {
        return month.get(Calendar.YEAR) * 12 + month.get(Calendar.MONTH)
    }

    private fun parseDate(date: String): Date? {
        return synchronized(dateFormat) {
            runCatching { dateFormat.parse(date) }.getOrNull()
        }
    }
}
