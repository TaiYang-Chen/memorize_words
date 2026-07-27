package com.chen.memorizewords.feature.home.ui.practice

import com.chen.memorizewords.core.common.resource.ResourceProvider
import com.chen.memorizewords.domain.practice.PracticeDailyDurationStats
import com.chen.memorizewords.domain.practice.PracticeEntryType
import com.chen.memorizewords.domain.practice.PracticeMode
import com.chen.memorizewords.domain.practice.PracticeSessionRecord
import com.chen.memorizewords.domain.practice.usage.EvaluationPolicy
import com.chen.memorizewords.domain.practice.usage.EvaluationTier
import com.chen.memorizewords.domain.practice.usage.EvaluationUsage
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
    fun todayGoalPercentCoversZeroPartialReachedAndExceeded() {
        assertEquals(0, mapper.calculateTodayGoalPercent(0L))
        assertEquals(50, mapper.calculateTodayGoalPercent(15 * 60_000L))
        assertEquals(100, mapper.calculateTodayGoalPercent(30 * 60_000L))
        assertEquals(100, mapper.calculateTodayGoalPercent(45 * 60_000L))
    }

    @Test
    fun dashboardKeepsActualMinutesWhenGoalIsExceeded() {
        val ui = mapper.buildDashboardUi(
            todayDurationMs = 45 * 60_000L,
            continuousDays = 0,
            recentStats = emptyList(),
            totalDurationMs = 0L
        )

        assertEquals("45", ui.todayDurationValue)
        assertEquals("45 / 30 分钟", ui.todayProgressText)
        assertEquals("100%", ui.todayPercentText)
        assertEquals(1_800, ui.todayProgress)
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
            recentStats = weeklyStats(previousMinutesPerDay = 10, currentMinutesPerDay = 20),
            totalDurationMs = 250 * 60_000L
        )

        assertEquals("140", ui.weekDurationValue)
        assertEquals("分钟", ui.weekDurationUnit)
        assertEquals("+100%", ui.weekTrendText)
        assertEquals(PracticeTrendDirection.UP, ui.weekTrendDirection)
        assertEquals("3", ui.continuousDaysText)
        assertEquals("Lv.3", ui.levelText)
        assertEquals("50 / 100 XP", ui.xpText)
    }

    @Test
    fun weeklyTrendSupportsNegativeFlatAndZeroPreviousPeriod() {
        val negative = mapper.buildDashboardUi(
            0L,
            0,
            weeklyStats(previousMinutesPerDay = 20, currentMinutesPerDay = 10),
            0L
        )
        val flat = mapper.buildDashboardUi(
            0L,
            0,
            weeklyStats(previousMinutesPerDay = 10, currentMinutesPerDay = 10),
            0L
        )
        val noPrevious = mapper.buildDashboardUi(
            0L,
            0,
            weeklyStats(previousMinutesPerDay = 0, currentMinutesPerDay = 5),
            0L
        )

        assertEquals("-50%", negative.weekTrendText)
        assertEquals(PracticeTrendDirection.DOWN, negative.weekTrendDirection)
        assertEquals("0%", flat.weekTrendText)
        assertEquals(PracticeTrendDirection.FLAT, flat.weekTrendDirection)
        assertEquals("100%+", noPrevious.weekTrendText)
        assertEquals(PracticeTrendDirection.UP, noPrevious.weekTrendDirection)
    }

    @Test
    fun sessionMappingKeepsEachPracticeModeIconAndTint() {
        val modes = PracticeMode.entries
        val mapped = mapper.buildSessionUi(
            modes.mapIndexed { index, mode ->
                PracticeSessionRecord(
                    id = index.toLong(),
                    date = "2026-07-26",
                    mode = mode,
                    entryType = PracticeEntryType.RANDOM,
                    entryCount = 10,
                    durationMs = 5 * 60_000L,
                    createdAtMs = 1_774_704_000_000L,
                    wordIds = emptyList()
                )
            }
        )

        assertEquals(
            listOf(
                R.drawable.feature_home_ic_practice_headset,
                R.drawable.feature_home_ic_practice_mic,
                R.drawable.feature_home_ic_practice_edit,
                R.drawable.feature_home_ic_practice_play,
                R.drawable.feature_home_ic_practice_exam
            ),
            mapped.map { it.iconRes }
        )
        assertEquals(
            listOf(
                R.color.practice_record_icon_listening,
                R.color.practice_record_icon_shadowing,
                R.color.practice_record_icon_spelling,
                R.color.practice_record_icon_audio_loop,
                R.color.practice_record_icon_exam
            ),
            mapped.map { it.iconTintRes }
        )
    }

    @Test
    fun shadowingQuotaBadgeMapsUnknownRemainingAndExhaustedStates() {
        assertEquals(
            PracticeQuotaBadgeUi(R.string.feature_home_shadowing_quota_badge_unknown),
            buildPracticeQuotaBadgeUi(null)
        )
        assertEquals(
            PracticeQuotaBadgeUi(
                R.string.feature_home_shadowing_quota_badge_remaining,
                remaining = 4
            ),
            buildPracticeQuotaBadgeUi(evaluationUsage(remaining = 4))
        )
        assertEquals(
            PracticeQuotaBadgeUi(R.string.feature_home_shadowing_quota_badge_exhausted),
            buildPracticeQuotaBadgeUi(evaluationUsage(remaining = 0))
        )
    }

    private fun weeklyStats(
        previousMinutesPerDay: Long,
        currentMinutesPerDay: Long
    ): List<PracticeDailyDurationStats> {
        return List(14) { index ->
            val minutes = if (index < 7) previousMinutesPerDay else currentMinutesPerDay
            PracticeDailyDurationStats(
                date = "2026-07-${(index + 1).toString().padStart(2, '0')}",
                durationMs = minutes * 60_000L
            )
        }
    }

    private fun evaluationUsage(remaining: Int): EvaluationUsage {
        return EvaluationUsage(
            tier = EvaluationTier.FREE,
            dailyLimit = 10,
            used = (10 - remaining).coerceAtLeast(0),
            remaining = remaining,
            resetAtMs = 0L,
            policy = EvaluationPolicy(freeDailyLimit = 10, memberDailyLimit = 100)
        )
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
                R.string.feature_home_practice_goal_progress -> "${formatArgs[0]} / 30 分钟"
                R.string.home_practice_mode_listening -> "听力"
                R.string.home_practice_mode_shadowing -> "跟读"
                R.string.home_practice_mode_spelling -> "拼写"
                R.string.home_practice_mode_audio_loop -> "随身听"
                R.string.home_practice_mode_exam -> "真题"
                R.string.home_practice_entry_self -> "自选 ${formatArgs[0]} 词"
                R.string.home_practice_entry_random -> "随机 ${formatArgs[0]} 词"
                R.string.home_practice_record_title -> "${formatArgs[0]} · ${formatArgs[1]}"
                R.string.practice_record_mode_listening -> "听力"
                R.string.practice_record_mode_shadowing -> "跟读"
                R.string.practice_record_mode_spelling -> "拼写"
                R.string.practice_record_mode_audio_loop -> "随身听"
                R.string.practice_record_mode_exam -> "真题"
                else -> formatArgs.joinToString()
            }
        }
    }
}
