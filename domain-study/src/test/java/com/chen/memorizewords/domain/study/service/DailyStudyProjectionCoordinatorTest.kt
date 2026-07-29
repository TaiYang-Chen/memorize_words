package com.chen.memorizewords.domain.study.service

import com.chen.memorizewords.domain.study.model.learning.DailyPlanTargets
import com.chen.memorizewords.domain.study.model.learning.DailyProgressEvaluation
import com.chen.memorizewords.domain.study.model.learning.DailyProgressProjectionTask
import com.chen.memorizewords.domain.study.model.learning.DailyProgressTransition
import com.chen.memorizewords.domain.study.model.learning.DailyWordCounts
import com.chen.memorizewords.domain.study.model.record.CheckInRecord
import com.chen.memorizewords.domain.study.model.record.CheckInType
import com.chen.memorizewords.domain.study.model.record.DailyStudyWordRecord
import com.chen.memorizewords.domain.study.model.record.DailyWordStats
import com.chen.memorizewords.domain.study.repository.record.BusinessDateProvider
import com.chen.memorizewords.domain.study.repository.record.DailyStudyProjectionQueue
import com.chen.memorizewords.domain.study.repository.record.DailyStudyProjectionStore
import com.chen.memorizewords.domain.study.repository.record.StudyRecordQuery
import com.chen.memorizewords.domain.wordbook.model.study.StudyPlan
import com.chen.memorizewords.domain.wordbook.repository.StudyPlanRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class DailyStudyProjectionCoordinatorTest {
    @Test
    fun `drain consumes tasks sequentially in queue order`() = runBlocking {
        val queue = MutableQueue(mutableListOf(task("event-1", 1), task("event-2", 2)))
        val store = RecordingStore()
        val coordinator = coordinator(queue, store)

        coordinator.drainPending()

        assertEquals(listOf(1, 2), store.newCounts)
        assertEquals(emptyList(), queue.tasks)
    }

    @Test
    fun `drain processes more than one queue page`() = runBlocking {
        val queue = MutableQueue(
            (1..205).map { index -> task("event-$index", index) }.toMutableList()
        )
        val store = RecordingStore()

        coordinator(queue, store).drainPending()

        assertEquals((1..205).toList(), store.newCounts)
        assertEquals(emptyList(), queue.tasks)
    }

    @Test
    fun `task replays safely when deletion fails after projection`() = runBlocking {
        val queue = MutableQueue(mutableListOf(task("event-1", 15)), failFirstDelete = true)
        val store = IdempotentCheckInStore()
        val coordinator = coordinator(queue, store)

        val first = coordinator.drainPending().single()
        val replay = coordinator.drainPending().single()

        assertIs<DailyProgressTransition.CheckInCreated>(first)
        assertIs<DailyProgressTransition.AlreadyCheckedIn>(replay)
        assertEquals(2, store.applyCount)
        assertEquals(emptyList(), queue.tasks)
    }

    @Test
    fun `projection failure retains current and later tasks for recovery`() = runBlocking {
        val queue = MutableQueue(mutableListOf(task("event-1", 15), task("event-2", 16)))
        val coordinator = coordinator(queue, ThrowingStore)

        val transitions = coordinator.drainPending()

        assertEquals(1, transitions.size)
        assertIs<DailyProgressTransition.RecoveryRequired>(transitions.single())
        assertEquals(listOf("event-1", "event-2"), queue.tasks.map { it.clientEventId })
    }

    @Test
    fun `shared write coordinator gives plan and event writes a deterministic order`() = runBlocking {
        val writeCoordinator = DailyProgressWriteCoordinator()
        val firstEntered = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        val events = mutableListOf<String>()

        val planWrite = launch {
            writeCoordinator.withWrite {
                events += "plan-save"
                firstEntered.complete(Unit)
                releaseFirst.await()
                events += "plan-reconcile"
            }
        }
        firstEntered.await()
        val eventWrite = launch {
            writeCoordinator.withWrite {
                events += "event-read-plan"
                events += "event-commit"
            }
        }

        assertEquals(listOf("plan-save"), events)
        releaseFirst.complete(Unit)
        planWrite.join()
        eventWrite.join()
        assertEquals(
            listOf("plan-save", "plan-reconcile", "event-read-plan", "event-commit"),
            events
        )
    }

    private fun coordinator(
        queue: DailyStudyProjectionQueue,
        store: DailyStudyProjectionStore
    ) = DailyStudyProjectionCoordinator(
        writeCoordinator = DailyProgressWriteCoordinator(),
        projectionQueue = queue,
        projectionStore = store,
        studyRecordQuery = EmptyRecordQuery,
        studyPlanRepository = FixedPlanRepository,
        businessDateProvider = FixedDateProvider
    )

    private fun task(id: String, newCount: Int) = DailyProgressProjectionTask(
        clientEventId = id,
        clientSequence = newCount.toLong(),
        businessDate = DATE,
        counts = DailyWordCounts(newCount, 0),
        targets = DailyPlanTargets(15, 30),
        createdAtMs = newCount.toLong()
    )

    private class MutableQueue(
        val tasks: MutableList<DailyProgressProjectionTask>,
        private var failFirstDelete: Boolean = false
    ) : DailyStudyProjectionQueue {
        override suspend fun getById(clientEventId: String) =
            tasks.firstOrNull { it.clientEventId == clientEventId }
        override suspend fun getPending(limit: Int) = tasks.take(limit)
        override suspend fun delete(clientEventId: String) {
            if (failFirstDelete) {
                failFirstDelete = false
                error("simulated crash before task deletion")
            }
            tasks.removeAll { it.clientEventId == clientEventId }
        }
    }

    private class RecordingStore : DailyStudyProjectionStore {
        val newCounts = mutableListOf<Int>()
        override suspend fun apply(evaluation: DailyProgressEvaluation): DailyProgressTransition {
            newCounts += evaluation.counts.newCount
            return DailyProgressTransition.NotEligible(evaluation.businessDate)
        }
    }

    private class IdempotentCheckInStore : DailyStudyProjectionStore {
        var applyCount = 0
        override suspend fun apply(evaluation: DailyProgressEvaluation): DailyProgressTransition {
            applyCount++
            val record = CheckInRecord(DATE, CheckInType.AUTO, 1L, 1L)
            return if (applyCount == 1) {
                DailyProgressTransition.CheckInCreated(DATE, record)
            } else {
                DailyProgressTransition.AlreadyCheckedIn(DATE, record)
            }
        }
    }

    private object ThrowingStore : DailyStudyProjectionStore {
        override suspend fun apply(evaluation: DailyProgressEvaluation): DailyProgressTransition =
            error("database unavailable")
    }

    private object EmptyRecordQuery : StudyRecordQuery {
        override fun getStudyTotalDayCount(): Flow<Int> = emptyFlow()
        override fun getNewWordCount(date: String): Flow<Int> = emptyFlow()
        override fun getReviewWordCount(date: String): Flow<Int> = emptyFlow()
        override suspend fun getWordCounts(date: String) = DailyWordCounts(0, 0)
        override fun getDailyWordStats(startDate: String, endDate: String): Flow<List<DailyWordStats>> = emptyFlow()
        override fun getDailyStudyWordRecords(date: String): Flow<List<DailyStudyWordRecord>> = emptyFlow()
        override fun observeStudyDatesBetween(startDate: String, endDate: String): Flow<List<String>> = emptyFlow()
    }

    private object FixedPlanRepository : StudyPlanRepository {
        override suspend fun saveStudyPlan(studyPlan: StudyPlan) = Unit
        override suspend fun getStudyPlan(): StudyPlan = StudyPlan(15, 30)
        override fun getStudyPlanFlow(): Flow<StudyPlan?> = flowOf(StudyPlan(15, 30))
        override suspend fun saveStudyCount(dailyNewCount: Int, dailyReviewCount: Int) = Unit
    }

    private object FixedDateProvider : BusinessDateProvider {
        override fun currentBusinessDate(): String = DATE
    }

    private companion object {
        const val DATE = "2026-07-29"
    }
}
