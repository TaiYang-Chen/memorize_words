package com.chen.memorizewords.feature.home.ui.practice

import com.chen.memorizewords.core.common.resource.ResourceProvider
import com.chen.memorizewords.domain.practice.PracticeDailyDurationStats
import com.chen.memorizewords.feature.home.R
import kotlin.test.Test
import kotlin.test.assertEquals

class PracticeUiMapperTest {

    private val mapper = PracticeUiMapper(FakeResourceProvider)

    @Test
    fun formatDurationPartsUsesSecondsMinutesAndHours() {
        assertEquals(PracticeDurationParts("29", "秒"), mapper.formatDurationParts(29_000L))
        assertEquals(PracticeDurationParts("12", "分钟"), mapper.formatDurationParts(12 * 60_000L))
        assertEquals(PracticeDurationParts("1.5", "小时"), mapper.formatDurationParts(90 * 60_000L))
    }

    @Test
    fun todayGoalProgressCapsAtThirtyMinutes() {
        assertEquals(29, mapper.calculateTodayGoalProgress(29_000L))
        assertEquals(1_800, mapper.calculateTodayGoalProgress(45 * 60_000L))
    }

    @Test
    fun practiceLevelIsDerivedFromTotalPracticeMinutes() {
        assertEquals(PracticeLevelUi("Lv.1", "0 / 100 XP", 0), mapper.buildPracticeLevel(0L))
        assertEquals(PracticeLevelUi("Lv.1", "99 / 100 XP", 99), mapper.buildPracticeLevel(99 * 60_000L))
        assertEquals(PracticeLevelUi("Lv.2", "0 / 100 XP", 0), mapper.buildPracticeLevel(100 * 60_000L))
        assertEquals(PracticeLevelUi("Lv.3", "50 / 100 XP", 50), mapper.buildPracticeLevel(250 * 60_000L))
    }

    @Test
    fun dashboardUsesRecentStatsForWeekDurationAndTrend() {
        val ui = mapper.buildDashboardUi(
            todayDurationMs = 29_000L,
            continuousDays = 3,
            recentStats = listOf(
                PracticeDailyDurationStats("2026-06-21", 10 * 60_000L),
                PracticeDailyDurationStats("2026-06-22", 20 * 60_000L),
                PracticeDailyDurationStats("2026-06-23", 30 * 60_000L)
            ),
            totalDurationMs = 250 * 60_000L
        )

        assertEquals("60", ui.weekDurationValue)
        assertEquals("分钟", ui.weekDurationUnit)
        assertEquals("+50%", ui.weekTrendText)
        assertEquals("3", ui.continuousDaysText)
        assertEquals("Lv.3", ui.levelText)
        assertEquals("50 / 100 XP", ui.xpText)
    }

    private object FakeResourceProvider : ResourceProvider {
        override fun getString(resId: Int, vararg formatArgs: Any): String {
            return when (resId) {
                R.string.feature_home_practice_static_second -> "秒"
                R.string.home_minutes_unit -> "分钟"
                R.string.feature_home_practice_static_hours -> "小时"
                R.string.home_duration_hours_minutes -> "${formatArgs[0]}小时${formatArgs[1]}分钟"
                R.string.home_duration_hours -> "${formatArgs[0]}小时"
                R.string.home_duration_minutes -> "${formatArgs[0]}分钟"
                else -> formatArgs.joinToString()
            }
        }
    }
}
