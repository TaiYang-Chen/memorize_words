package com.chen.memorizewords.domain.study.usecase.plan

import com.chen.memorizewords.domain.study.model.learning.DailyProgressEvaluation
import com.chen.memorizewords.domain.study.model.learning.DailyProgressProjectionTask
import com.chen.memorizewords.domain.study.model.learning.DailyProgressTransition
import com.chen.memorizewords.domain.study.model.learning.DailyWordCounts
import com.chen.memorizewords.domain.study.model.record.DailyStudyWordRecord
import com.chen.memorizewords.domain.study.model.record.DailyWordStats
import com.chen.memorizewords.domain.study.repository.record.BusinessDateProvider
import com.chen.memorizewords.domain.study.repository.record.DailyStudyProjectionQueue
import com.chen.memorizewords.domain.study.repository.record.DailyStudyProjectionStore
import com.chen.memorizewords.domain.study.repository.record.StudyRecordQuery
import com.chen.memorizewords.domain.study.service.DailyProgressWriteCoordinator
import com.chen.memorizewords.domain.study.service.DailyStudyProjectionCoordinator
import com.chen.memorizewords.domain.wordbook.model.study.StudyPlan
import com.chen.memorizewords.domain.wordbook.repository.StudyPlanRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking

class UpdateDailyStudyTargetsUseCaseTest {
    @Test
    fun `saving lower targets immediately reconciles current day after save`() = runBlocking {
        val calls = mutableListOf<String>()
        val planRepository = RecordingPlanRepository(calls)
        val store = RecordingStore(calls)
        val writeCoordinator = DailyProgressWriteCoordinator()
        val projectionCoordinator = DailyStudyProjectionCoordinator(
            writeCoordinator = writeCoordinator,
            projectionQueue = EmptyQueue,
            projectionStore = store,
            studyRecordQuery = TenNewWordsQuery,
            studyPlanRepository = planRepository,
            businessDateProvider = TodayProvider
        )
        val useCase = UpdateDailyStudyTargetsUseCase(
            studyPlanRepository = planRepository,
            writeCoordinator = writeCoordinator,
            projectionCoordinator = projectionCoordinator
        )

        useCase(dailyNewCount = 10, dailyReviewCount = 30)

        assertEquals(listOf("save", "reconcile"), calls)
        assertEquals(10, store.evaluation?.targets?.newTarget)
        assertEquals(10, store.evaluation?.counts?.newCount)
    }

    private class RecordingPlanRepository(
        private val calls: MutableList<String>
    ) : StudyPlanRepository {
        private var plan = StudyPlan()
        override suspend fun saveStudyPlan(studyPlan: StudyPlan) { plan = studyPlan }
        override suspend fun getStudyPlan(): StudyPlan = plan
        override fun getStudyPlanFlow(): Flow<StudyPlan?> = flowOf(plan)
        override suspend fun saveStudyCount(dailyNewCount: Int, dailyReviewCount: Int) {
            calls += "save"
            plan = plan.copy(
                dailyNewCount = dailyNewCount,
                dailyReviewCount = dailyReviewCount
            )
        }
    }

    private class RecordingStore(
        private val calls: MutableList<String>
    ) : DailyStudyProjectionStore {
        var evaluation: DailyProgressEvaluation? = null
        override suspend fun apply(evaluation: DailyProgressEvaluation): DailyProgressTransition {
            calls += "reconcile"
            this.evaluation = evaluation
            return DailyProgressTransition.NotEligible(evaluation.businessDate)
        }
    }

    private object TenNewWordsQuery : StudyRecordQuery {
        override fun getStudyTotalDayCount(): Flow<Int> = emptyFlow()
        override fun getNewWordCount(date: String): Flow<Int> = emptyFlow()
        override fun getReviewWordCount(date: String): Flow<Int> = emptyFlow()
        override suspend fun getWordCounts(date: String) = DailyWordCounts(10, 0)
        override fun getDailyWordStats(startDate: String, endDate: String): Flow<List<DailyWordStats>> = emptyFlow()
        override fun getDailyStudyWordRecords(date: String): Flow<List<DailyStudyWordRecord>> = emptyFlow()
        override fun observeStudyDatesBetween(startDate: String, endDate: String): Flow<List<String>> = emptyFlow()
    }

    private object EmptyQueue : DailyStudyProjectionQueue {
        override suspend fun getById(clientEventId: String): DailyProgressProjectionTask? = null
        override suspend fun getPending(limit: Int): List<DailyProgressProjectionTask> = emptyList()
        override suspend fun delete(clientEventId: String) = Unit
    }

    private object TodayProvider : BusinessDateProvider {
        override fun currentBusinessDate(): String = "2026-07-29"
    }
}
