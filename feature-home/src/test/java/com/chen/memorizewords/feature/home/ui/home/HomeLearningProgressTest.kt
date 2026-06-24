package com.chen.memorizewords.feature.home.ui.home

import com.chen.memorizewords.domain.study.model.learning.LearningSessionRequest
import com.chen.memorizewords.domain.wordbook.model.WordBookInfo
import com.chen.memorizewords.domain.wordbook.model.study.StudyPlan
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HomeLearningProgressTest {

    @Test
    fun `new plan starts from zero when no words learned today`() {
        val progress = resolveNewLearningProgress(
            todayNewCount = 0,
            plan = StudyPlan(dailyNewCount = 5)
        )

        assertEquals(5, progress.remainingCount)
        assertEquals(0, progress.initialLearnedCount)
        assertFalse(shouldShowBoostNewWordsDialog(0, StudyPlan(dailyNewCount = 5)))
    }

    @Test
    fun `new plan continues from todays learned count`() {
        val progress = resolveNewLearningProgress(
            todayNewCount = 2,
            plan = StudyPlan(dailyNewCount = 5)
        )

        assertEquals(3, progress.remainingCount)
        assertEquals(2, progress.initialLearnedCount)
    }

    @Test
    fun `review plan starts from zero when no words reviewed today`() {
        val progress = resolveReviewLearningProgress(
            todayReviewCount = 0,
            plan = StudyPlan(dailyReviewCount = 30)
        )

        assertEquals(30, progress.remainingCount)
        assertEquals(0, progress.initialLearnedCount)
    }

    @Test
    fun `review plan continues from todays reviewed count`() {
        val progress = resolveReviewLearningProgress(
            todayReviewCount = 1,
            plan = StudyPlan(dailyReviewCount = 30)
        )

        assertEquals(29, progress.remainingCount)
        assertEquals(1, progress.initialLearnedCount)
    }

    @Test
    fun `review plan caps initial count when today review exceeds plan`() {
        val progress = resolveReviewLearningProgress(
            todayReviewCount = 35,
            plan = StudyPlan(dailyReviewCount = 30)
        )

        assertEquals(0, progress.remainingCount)
        assertEquals(30, progress.initialLearnedCount)
    }

    @Test
    fun `completed new plan uses boost flow instead of normal continue`() {
        val plan = StudyPlan(dailyNewCount = 5)

        assertTrue(shouldShowBoostNewWordsDialog(todayNewCount = 5, plan = plan))
        assertTrue(shouldShowBoostNewWordsDialog(todayNewCount = 6, plan = plan))
    }

    @Test
    fun `learning route preserves initial learned count from domain request`() {
        val route = createLearningRoute(
            LearningSessionRequest(
                initialLearnedCount = 2,
                wordIds = listOf(10L, 11L, 12L),
                sessionType = LearningSessionTypeContract.NEW,
                sessionWordCount = 3
            )
        )

        requireNotNull(route)
        assertEquals(2, route.initialLearnedCount)
        assertEquals(listOf(10L, 11L, 12L), route.wordIds)
        assertEquals(LearningSessionTypeContract.NEW, route.sessionType)
        assertEquals(3, route.sessionWordCount)
    }

    @Test
    fun `word book display uses placeholder before local info loads`() {
        assertEquals(WORD_BOOK_INFO_PLACEHOLDER, formatWordBookNumberText(null))
        assertEquals(WORD_BOOK_INFO_PLACEHOLDER, formatWordBookAccuracyRateText(null))
        assertEquals(
            WORD_BOOK_INFO_PLACEHOLDER,
            formatWordBookRemainDaysText(null, StudyPlan(dailyNewCount = 10))
        )
    }

    @Test
    fun `word book display shows local database values after info loads`() {
        val info = WordBookInfo(
            totalWords = 100,
            learningWords = 20,
            masteredWords = 6,
            studyDayCount = 3,
            accuracyRate = 75f
        )

        assertEquals("6", formatWordBookNumberText(info.masteredWords))
        assertEquals("80", formatWordBookNumberText(info.remainWords))
        assertEquals("8 天", formatWordBookRemainDaysText(info, StudyPlan(dailyNewCount = 10)))
        assertEquals("75.0%", formatWordBookAccuracyRateText(info.accuracyRate))
    }

    @Test
    fun `home dashboard keeps over-plan counts visible but caps percent`() {
        val plan = StudyPlan(dailyNewCount = 15, dailyReviewCount = 30)

        assertEquals("15/45", formatTodayCompletedWordsText(15, 0, plan))
        assertEquals("49/45", formatTodayCompletedWordsText(18, 31, plan))
        assertEquals(100, calculateLearnProgress(18, 31, plan))
    }

    @Test
    fun `home dashboard formats task states`() {
        assertEquals("待完成", formatTaskStatus(0, 15))
        assertEquals("进行中", formatTaskStatus(7, 15))
        assertEquals("已完成", formatTaskStatus(15, 15))
        assertEquals("已完成", formatTaskStatus(18, 15))
        assertEquals("暂无计划", formatTaskStatus(0, 0))
        assertTrue(isTaskDone(15, 15))
        assertFalse(isTaskDone(0, 0))
    }

    @Test
    fun `home dashboard formats review progress and remaining review words`() {
        val plan = StudyPlan(dailyNewCount = 15, dailyReviewCount = 30)

        assertEquals("0/30", formatCountProgressText(0, plan.dailyReviewCount))
        assertEquals(30, calculateRemainingCount(0, plan.dailyReviewCount))
        assertEquals(12, calculateRemainingCount(18, plan.dailyReviewCount))
    }

    @Test
    fun `home dashboard handles zero and negative plan counts`() {
        val plan = StudyPlan(dailyNewCount = -5, dailyReviewCount = -10)

        assertEquals(0, calculateTodayPlanTotalCount(plan))
        assertEquals(0, calculateLearnProgress(0, 0, plan))
        assertEquals("0/0", formatTodayCompletedWordsText(0, 0, plan))
        assertEquals("0/0", formatCountProgressText(0, 0))
        assertEquals("0分钟", formatEstimatedStudyMinutesText(plan))
    }

    @Test
    fun `expected completion days are rounded up`() {
        val info = WordBookInfo(totalWords = 101, learningWords = 20)

        assertEquals("9 天", formatExpectedCompletionText(info, StudyPlan(dailyNewCount = 10)))
        assertEquals("--", formatExpectedCompletionText(info, StudyPlan(dailyNewCount = 0)))
        assertEquals("已完成", formatExpectedCompletionText(info.copy(learningWords = 101), StudyPlan(dailyNewCount = 10)))
    }

    @Test
    fun `dashboard ui state uses real plan summary and task states`() {
        val info = WordBookInfo(
            title = "雅思核心词汇",
            totalWords = 100,
            learningWords = 20,
            masteredWords = 6,
            accuracyRate = 75f
        )

        val ui = buildHomeDashboardUiState(
            wordBookInfo = info,
            plan = StudyPlan(dailyNewCount = 15, dailyReviewCount = 30),
            todayNewCount = 15,
            todayReviewCount = 0,
            todayStudyDurationMs = 15 * 60 * 1000L,
            continuousDays = 2,
            totalStudyDays = 4
        )

        assertEquals("雅思核心词汇", ui.wordBookTitleText)
        assertEquals("新学 15 / 复习 30", ui.planCardSubtitleText)
        assertEquals("15/15", ui.todayNewProgressText)
        assertEquals("已完成", ui.todayNewStatusText)
        assertEquals("0/30", ui.todayReviewProgressText)
        assertEquals("待完成", ui.todayReviewStatusText)
        assertEquals("15分钟", ui.estimatedStudyMinutesText)
        assertEquals("2 天", ui.continuousDaysText)
        assertEquals("6 天", ui.expectedCompletionText)
    }
}
