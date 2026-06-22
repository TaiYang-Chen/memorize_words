package com.chen.memorizewords.feature.home.ui.stats

import com.chen.memorizewords.domain.study.model.record.DailyDurationStats
import com.chen.memorizewords.domain.study.model.record.DailyWordStats
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StatsDashboardUiTest {

    @Test
    fun formatHoursValue_roundsToOneDecimalAndDropsZeroFraction() {
        assertEquals("0", formatHoursValue(0L))
        assertEquals("1", formatHoursValue(3_600_000L))
        assertEquals("1.5", formatHoursValue(5_400_000L))
    }

    @Test
    fun buildOverviewCardsUsesRealCountsDurationAndAccuracy() {
        val cards = buildOverviewCards(
            totalWords = 12,
            streakDays = 3,
            totalDurationMs = 5_400_000L,
            todayWordCount = 4,
            todayDurationMs = 1_800_000L,
            accuracyRate = 76.24f
        )

        assertEquals("12", cards[0].value)
        assertEquals("今日 +4", cards[0].changeText)
        assertEquals("3", cards[1].value)
        assertEquals("1.5", cards[2].value)
        assertEquals("今日 +0.5h", cards[2].changeText)
        assertEquals("76.2", cards[3].value)
    }

    @Test
    fun buildTrendPointsAlignsStatsToWeekDates() {
        val range = StatsWeekRange(
            startDate = "2026-06-15",
            endDate = "2026-06-21",
            dates = listOf(
                "2026-06-15",
                "2026-06-16",
                "2026-06-17",
                "2026-06-18",
                "2026-06-19",
                "2026-06-20",
                "2026-06-21"
            )
        )

        val points = buildTrendPoints(
            weekRange = range,
            wordStats = listOf(DailyWordStats("2026-06-16", newCount = 8, reviewCount = 3)),
            durationStats = listOf(DailyDurationStats("2026-06-16", durationMs = 7_200_000L)),
            weekLabels = listOf("一", "二", "三", "四", "五", "六", "日")
        )

        assertEquals(7, points.size)
        assertEquals("二", points[1].dayLabel)
        assertEquals(2f, points[1].durationHours)
        assertEquals(8, points[1].newWordCount)
        assertEquals(0f, points[0].durationHours)
    }

    @Test
    fun buildTimeDistributionUsesBalancedEmptyState() {
        val distribution = buildTimeDistribution(emptyList())

        assertEquals(listOf(25, 25, 25, 25), distribution.map { it.percent })
        assertEquals(listOf("早晨", "上午", "下午", "晚上"), distribution.map { it.label })
    }

    @Test
    fun buildReportRowsSummarizesWeek() {
        val rows = buildReportRows(
            wordStats = listOf(
                DailyWordStats("2026-06-15", newCount = 3, reviewCount = 4),
                DailyWordStats("2026-06-16", newCount = 2, reviewCount = 1)
            ),
            durationStats = listOf(
                DailyDurationStats("2026-06-15", durationMs = 3_600_000L),
                DailyDurationStats("2026-06-16", durationMs = 7_200_000L)
            ),
            streakDays = 2,
            weekLabels = listOf("一", "二", "三", "四", "五", "六", "日")
        )

        assertEquals("3", rows[0].value)
        assertEquals("10", rows[1].value)
        assertEquals("二", rows[2].value)
        assertTrue(rows[3].value == "2")
    }
}
