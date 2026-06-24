package com.chen.memorizewords.feature.learning.ui.learning

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
        }

        persistenceStarted.await()
        val awaitJob = launch {
            gate.awaitPending()
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
        }

        persistenceStarted.await()
        val awaitJob = launch {
            gate.awaitPending()
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
                "navigate-finished"
            ),
            events
        )
    }
}
