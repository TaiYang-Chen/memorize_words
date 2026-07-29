package com.chen.memorizewords.feature.learning.ui.learning

import com.chen.memorizewords.domain.study.model.learning.DailyProgressTransition
import com.chen.memorizewords.domain.study.model.learning.LearningActivityCommitResult
import com.chen.memorizewords.domain.study.model.learning.RecordLearningEventResult
import com.chen.memorizewords.domain.study.model.record.CheckInRecord
import com.chen.memorizewords.domain.study.model.record.CheckInType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.runBlocking

class LearningFinishRouteTest {
    @Test
    fun `fourteenth word keeps normal completion route`() = runBlocking<Unit> {
        val route = finishRoute(
            commits = listOf(commit(DailyProgressTransition.NotEligible(DATE))),
            finalTransition = DailyProgressTransition.NotEligible(DATE)
        )

        assertIs<LearningViewModel.Route.ToLearningDone>(route)
    }

    @Test
    fun `fifteenth word checkin creation opens readonly checkin page`() = runBlocking<Unit> {
        val route = finishRoute(
            commits = listOf(commit(DailyProgressTransition.CheckInCreated(DATE, record()))),
            finalTransition = DailyProgressTransition.AlreadyCheckedIn(DATE, record())
        )

        val checkInRoute = assertIs<LearningViewModel.Route.ToCheckIn>(route)
        assertEquals(DATE, checkInRoute.businessDate)
    }

    @Test
    fun `additional learning on checked in day does not reopen checkin page`() = runBlocking<Unit> {
        val route = finishRoute(
            commits = listOf(commit(DailyProgressTransition.AlreadyCheckedIn(DATE, record()))),
            finalTransition = DailyProgressTransition.AlreadyCheckedIn(DATE, record())
        )

        assertIs<LearningViewModel.Route.ToLearningDone>(route)
    }

    private suspend fun finishRoute(
        commits: List<LearningActivityCommitResult>,
        finalTransition: DailyProgressTransition
    ) = resolveLearningFinishRoute(
        sessionTypeValue = 0,
        sessionWordCount = 15,
        answeredCount = 15,
        correctCount = 15,
        wrongCount = 0,
        studyDurationMs = 1_000L,
        wordIds = listOf(1L),
        activityCommits = commits,
        addStudyDuration = {},
        reconcileDailyProgress = { finalTransition }
    )

    private fun commit(transition: DailyProgressTransition) = LearningActivityCommitResult(
        learningEvent = RecordLearningEventResult("event", 1L, 1L, 1L, 1L),
        dailyProgress = transition
    )

    private fun record() = CheckInRecord(DATE, CheckInType.AUTO, 1L, 1L)

    private companion object {
        const val DATE = "2026-07-29"
    }
}
