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
        assertEquals("8", formatWordBookRemainDaysText(info, StudyPlan(dailyNewCount = 10)))
        assertEquals("75.0%", formatWordBookAccuracyRateText(info.accuracyRate))
    }
}
