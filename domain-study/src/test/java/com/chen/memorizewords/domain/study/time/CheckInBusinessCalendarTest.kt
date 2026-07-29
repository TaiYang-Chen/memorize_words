package com.chen.memorizewords.domain.study.time

import com.chen.memorizewords.core.common.calendar.CheckInBusinessCalendar
import com.chen.memorizewords.core.common.calendar.CheckInConfig
import com.chen.memorizewords.core.common.calendar.CheckInConfigDataSource
import com.chen.memorizewords.core.common.time.AppClock
import java.util.Calendar
import java.util.GregorianCalendar
import java.util.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class CheckInBusinessCalendarTest {
    @Test
    fun `business date changes exactly at four oclock`() {
        val clock = MutableClock(epochAt(hour = 3, minute = 59))
        val calendar = CheckInBusinessCalendar(FixedConfigSource(), clock)

        assertEquals("2026-07-28", calendar.currentBusinessDate())

        clock.now = epochAt(hour = 4, minute = 0)
        assertEquals("2026-07-29", calendar.currentBusinessDate())
    }

    @Test
    fun `streak uses the same shifted business date`() {
        val clock = MutableClock(epochAt(hour = 3, minute = 59))
        val calendar = CheckInBusinessCalendar(FixedConfigSource(), clock)

        assertEquals(2, calendar.calculateCurrentStreak(listOf("2026-07-28", "2026-07-27")))

        clock.now = epochAt(hour = 4, minute = 0)
        assertEquals(2, calendar.calculateCurrentStreak(listOf("2026-07-29", "2026-07-28")))
    }

    private class MutableClock(var now: Long) : AppClock {
        override fun nowEpochMillis(): Long = now
        override fun nowElapsedMillis(): Long = now
    }

    private class FixedConfigSource : CheckInConfigDataSource {
        private val config = CheckInConfig(
            dayBoundaryOffsetMinutes = 240,
            timezoneId = TIMEZONE.id
        )
        private val flow = MutableStateFlow(config)

        override fun getConfig(): CheckInConfig = config
        override fun getConfigFlow(): Flow<CheckInConfig> = flow
        override fun saveDayBoundaryOffsetMinutes(offsetMinutes: Int) = Unit
        override fun saveTimezoneId(timezoneId: String) = Unit
        override fun saveCachedMakeupCardBalance(balance: Int) = Unit
        override fun consumeCachedMakeupCardBalance(count: Int) = Unit
        override fun saveLastCheckInSyncAt(timestamp: Long) = Unit
        override fun clearUserScopedState() = Unit
    }

    private companion object {
        val TIMEZONE: TimeZone = TimeZone.getTimeZone("Asia/Shanghai")

        fun epochAt(hour: Int, minute: Int): Long = GregorianCalendar(TIMEZONE).apply {
            clear()
            set(2026, Calendar.JULY, 29, hour, minute, 0)
        }.timeInMillis
    }
}
