package com.chen.memorizewords.feature.home.ui.home

import com.chen.memorizewords.domain.study.model.learning.LearningSessionRequest
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
}
