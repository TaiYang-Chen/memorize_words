package com.chen.memorizewords.data.local.mmkv.checkin

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

@Singleton
class CheckInBusinessCalendar @Inject constructor(
    private val checkInConfigDataSource: CheckInConfigDataSource
) {

    fun currentBusinessDate(): String {
        return currentBusinessDate(resolvedConfig())
    }

    fun resolvedConfig(): CheckInConfig {
        return normalizeConfig(checkInConfigDataSource.getConfig())
    }

    fun observeResolvedConfig(): Flow<CheckInConfig> {
        return checkInConfigDataSource.getConfigFlow()
            .map(::normalizeConfig)
            .distinctUntilChanged()
    }

    fun currentBusinessDate(config: CheckInConfig): String {
        val timezone = resolveTimezone(config.timezoneId)
        val shiftedTimestamp =
            System.currentTimeMillis() - config.dayBoundaryOffsetMinutes.toLong() * 60_000L
        return formatDate(shiftedTimestamp, timezone)
    }

    fun buildRecentDateRange(dayCount: Int, config: CheckInConfig = resolvedConfig()): BusinessDateRange {
        val safeCount = dayCount.coerceAtLeast(1)
        val timezone = resolveTimezone(config.timezoneId)
        val shiftedTimestamp =
            System.currentTimeMillis() - config.dayBoundaryOffsetMinutes.toLong() * 60_000L
        val cursor = Calendar.getInstance(timezone).apply {
            timeInMillis = shiftedTimestamp
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val dates = mutableListOf<String>()
        repeat(safeCount) {
            dates.add(formatDate(cursor.timeInMillis, timezone))
            cursor.add(Calendar.DAY_OF_MONTH, -1)
        }
        val reversed = dates.reversed()
        return BusinessDateRange(
            startDate = reversed.first(),
            endDate = reversed.last(),
            dates = reversed
        )
    }

    fun calculateCurrentStreak(
        completedDates: List<String>,
        config: CheckInConfig = resolvedConfig()
    ): Int {
        if (completedDates.isEmpty()) return 0
        val completedDateSet = completedDates.toHashSet()
        val timezone = resolveTimezone(config.timezoneId)
        val cursor = Calendar.getInstance(timezone).apply {
            timeInMillis =
                System.currentTimeMillis() - config.dayBoundaryOffsetMinutes.toLong() * 60_000L
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        var streak = 0
        while (true) {
            val date = formatDate(cursor.timeInMillis, timezone)
            if (date !in completedDateSet) break
            streak++
            cursor.add(Calendar.DAY_OF_MONTH, -1)
        }
        return streak
    }

    private fun normalizeConfig(config: CheckInConfig): CheckInConfig {
        val timezoneId = resolveTimezoneId(config.timezoneId)
        return if (timezoneId == config.timezoneId) {
            config
        } else {
            config.copy(timezoneId = timezoneId)
        }
    }

    private fun formatDate(timestamp: Long, timezone: TimeZone): String {
        val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        formatter.timeZone = timezone
        return formatter.format(Date(timestamp))
    }

    private fun resolveTimezone(timezoneId: String): TimeZone {
        return TimeZone.getTimeZone(resolveTimezoneId(timezoneId))
    }

    private fun resolveTimezoneId(timezoneId: String): String {
        if (timezoneId.isBlank()) return TimeZone.getDefault().id
        val timezone = TimeZone.getTimeZone(timezoneId)
        return if (timezone.id == "GMT" && timezoneId != "GMT") {
            TimeZone.getDefault().id
        } else {
            timezone.id
        }
    }

}

data class BusinessDateRange(
    val startDate: String,
    val endDate: String,
    val dates: List<String>
)
