package com.chen.memorizewords.data.sync.repository.sync

import com.chen.memorizewords.data.sync.local.room.model.sync.SyncOutboxDao
import com.chen.memorizewords.data.wordbook.local.room.model.learning.outbox.LearningOutboxDao
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
class UnifiedSyncEngine @Inject constructor(
    private val learningOutboxProcessor: LearningOutboxProcessor,
    private val syncOutboxProcessor: SyncOutboxProcessor,
    private val learningOutboxDao: LearningOutboxDao,
    private val syncOutboxDao: SyncOutboxDao
) {
    private val drainMutex = Mutex()

    suspend fun drain(): DrainOutcome = drainMutex.withLock {
        var processedAny = false
        var rounds = 0
        var reachedWorkLimit = true

        while (rounds < MAX_DRAIN_ROUNDS) {
            rounds++
            val learningResult = learningOutboxProcessor.drainBatch()
            val globalResult = syncOutboxProcessor.drainBatch()

            processedAny = processedAny || learningResult.didProcessBatch() || globalResult.didProcessBatch()
            if (
                learningResult == SyncOutboxProcessor.DrainResult.Empty &&
                globalResult == SyncOutboxProcessor.DrainResult.Empty
            ) {
                reachedWorkLimit = false
                break
            }
        }

        val now = System.currentTimeMillis()
        val nextRetryAt = listOfNotNull(
            learningOutboxDao.getEarliestRetryAt(now),
            syncOutboxDao.getEarliestRetryAt(now)
        ).minOrNull()

        DrainOutcome(
            processedAny = processedAny,
            nextRetryAtMs = nextRetryAt,
            reachedWorkLimit = reachedWorkLimit
        )
    }

    data class DrainOutcome(
        val processedAny: Boolean,
        val nextRetryAtMs: Long?,
        val reachedWorkLimit: Boolean
    )

    private fun SyncOutboxProcessor.DrainResult.didProcessBatch(): Boolean {
        return this != SyncOutboxProcessor.DrainResult.Empty
    }

    private companion object {
        const val MAX_DRAIN_ROUNDS = 12
    }
}
