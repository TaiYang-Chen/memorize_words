package com.chen.memorizewords.feature.learning.ui.learning

import com.chen.memorizewords.domain.study.model.learning.DailyProgressTransition
import com.chen.memorizewords.domain.study.model.learning.LearningActivityCommitResult
import com.chen.memorizewords.domain.study.model.learning.RecordLearningEventResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class LearningCompletionPersistenceGateTest {

    @Test
    fun `awaitPending waits for mastered word persistence before continuing`() = runBlocking {
        val gate = LearningCompletionPersistenceGate()
        val releasePersistence = CompletableDeferred<Unit>()
        val persistenceStarted = CompletableDeferred<Unit>()
        val events = mutableListOf<String>()

        gate.launch(this) {
            events += "persist-mastered-started"
            persistenceStarted.complete(Unit)
            releasePersistence.await()
            events += "persist-mastered-finished"
            commit("event-mastered")
        }

        persistenceStarted.await()
        val awaitJob = launch {
            val commits = gate.awaitPending()
            events += commits.single().learningEvent.clientEventId
            events += "navigate-finished"
        }

        assertEquals(
            listOf("persist-mastered-started"),
            events
        )

        releasePersistence.complete(Unit)
        awaitJob.join()

        assertEquals(
            listOf(
                "persist-mastered-started",
                "persist-mastered-finished",
                "event-mastered",
                "navigate-finished"
            ),
            events
        )
    }

    @Test
    fun `awaitPending waits for answered word persistence before continuing`() = runBlocking {
        val gate = LearningCompletionPersistenceGate()
        val releasePersistence = CompletableDeferred<Unit>()
        val persistenceStarted = CompletableDeferred<Unit>()
        val events = mutableListOf<String>()

        gate.launch(this) {
            events += "persist-learned-started"
            persistenceStarted.complete(Unit)
            releasePersistence.await()
            events += "persist-learned-finished"
            commit("event-learned")
        }

        persistenceStarted.await()
        val awaitJob = launch {
            val commits = gate.awaitPending()
            events += commits.single().learningEvent.clientEventId
            events += "navigate-finished"
        }

        assertEquals(
            listOf("persist-learned-started"),
            events
        )

        releasePersistence.complete(Unit)
        awaitJob.join()

        assertEquals(
            listOf(
                "persist-learned-started",
                "persist-learned-finished",
                "event-learned",
                "navigate-finished"
            ),
            events
        )
    }

    private fun commit(eventId: String) = LearningActivityCommitResult(
        learningEvent = RecordLearningEventResult(
            clientEventId = eventId,
            wordId = 1L,
            bookId = 1L,
            stateRevision = 1L,
            progressRevision = 1L
        ),
        dailyProgress = DailyProgressTransition.NotEligible("2026-07-29")
    )
}
