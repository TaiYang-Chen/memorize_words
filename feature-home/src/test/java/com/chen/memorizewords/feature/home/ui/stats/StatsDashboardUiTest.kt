package com.chen.memorizewords.feature.home.ui.stats

import com.chen.memorizewords.domain.study.model.record.DailyDurationStats
import com.chen.memorizewords.domain.study.model.record.DailyWordStats
import com.chen.memorizewords.domain.study.model.record.CalendarDayStats
import com.chen.memorizewords.core.common.resource.ResourceProvider
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
    fun buildOverviewCardsUsesUnifiedCurrentBookMetrics() {
        val cards = buildOverviewCards(
            learnedWords = 21,
            learningWords = 13,
            masteredWords = 8,
            accuracyRate = 76.24f
        )

        assertEquals("21", cards[0].value)
        assertEquals("已学习", cards[0].title)
        assertEquals("学习中 13 · 已掌握 8", cards[0].changeText)
        assertEquals("13", cards[1].value)
        assertEquals("8", cards[2].value)
        assertEquals("76.2", cards[3].value)
        assertEquals("答题正确率", cards[3].title)
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
    fun buildTimeDistributionReturnsEmptyUntilRealTimeBucketsExist() {
        val distribution = buildTimeDistribution(emptyList())

        assertTrue(distribution.isEmpty())
    }

    @Test
    fun buildCalendarPagerPagesMapsRealCalendarStatsToStatuses() {
        val dateCalculator = StatsDateCalculator()
        val builder = StatsCalendarBuilder(
            dateCalculator = dateCalculator,
            formatter = StatsFormatter(FakeResourceProvider)
        )

        val pages = builder.buildCalendarPagerPages(
            anchorMonth = dateCalculator.currentMonthStart("2026-06-22"),
            stats = listOf(
                CalendarDayStats(
                    date = "2026-06-03",
                    hasStudy = true,
                    hasCheckIn = false,
                    isNewPlanCompleted = false,
                    isReviewPlanCompleted = false
                ),
                CalendarDayStats(
                    date = "2026-06-04",
                    hasStudy = true,
                    hasCheckIn = false,
                    isNewPlanCompleted = true,
                    isReviewPlanCompleted = false
                ),
                CalendarDayStats(
                    date = "2026-06-05",
                    hasStudy = true,
                    hasCheckIn = false,
                    isNewPlanCompleted = true,
                    isReviewPlanCompleted = true
                ),
                CalendarDayStats(
                    date = "2026-06-06",
                    hasStudy = false,
                    hasCheckIn = true,
                    isNewPlanCompleted = false,
                    isReviewPlanCompleted = false
                )
            ),
            currentBusinessDate = "2026-06-22"
        )

        val currentMonthCells = pages[1].cells.associateBy { it.date }
        assertEquals(CalendarStudyStatus.STUDIED, currentMonthCells["2026-06-03"]?.status)
        assertEquals(CalendarStudyStatus.NEW_DONE, currentMonthCells["2026-06-04"]?.status)
        assertEquals(CalendarStudyStatus.ALL_DONE, currentMonthCells["2026-06-05"]?.status)
        assertEquals(CalendarStudyStatus.CHECKED_IN, currentMonthCells["2026-06-06"]?.status)
        assertTrue(currentMonthCells["2026-06-22"]?.isToday == true)
    }

    @Test
    fun heatmapDayLabelShowsDateTodayAndBlanksOutsideMonth() {
        assertEquals(
            "18",
            heatmapDayLabel(
                CalendarDayCellUi(
                    date = "2026-06-18",
                    dayText = "18",
                    isCurrentMonth = true,
                    isToday = false,
                    isSelected = false,
                    status = CalendarStudyStatus.NONE
                )
            )
        )
        assertEquals(
            "今",
            heatmapDayLabel(
                CalendarDayCellUi(
                    date = "2026-06-26",
                    dayText = "26",
                    isCurrentMonth = true,
                    isToday = true,
                    isSelected = false,
                    status = CalendarStudyStatus.STUDIED
                )
            )
        )
        assertEquals(
            "",
            heatmapDayLabel(
                CalendarDayCellUi(
                    date = "2026-05-31",
                    dayText = "31",
                    isCurrentMonth = false,
                    isToday = false,
                    isSelected = false,
                    status = CalendarStudyStatus.NONE
                )
            )
        )
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

    private object FakeResourceProvider : ResourceProvider {
        override fun getString(resId: Int, vararg formatArgs: Any): String {
            return when (formatArgs.size) {
                0 -> resId.toString()
                else -> formatArgs.joinToString()
            }
        }
    }
}
