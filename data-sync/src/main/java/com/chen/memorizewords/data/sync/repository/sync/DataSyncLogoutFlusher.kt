package com.chen.memorizewords.data.sync.repository.sync

import com.chen.memorizewords.data.sync.local.room.model.sync.SyncOutboxDao
import com.chen.memorizewords.data.wordbook.local.room.model.learning.outbox.LearningOutboxDao
import com.chen.memorizewords.data.wordbook.local.room.model.learning.outbox.LearningOutboxEntity
import com.chen.memorizewords.domain.sync.SyncDrainOutcome
import com.chen.memorizewords.domain.sync.SyncLogoutFlusher
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DataSyncLogoutFlusher @Inject constructor(
    private val syncOutboxDao: SyncOutboxDao,
    private val learningOutboxDao: LearningOutboxDao,
    private val unifiedSyncEngine: UnifiedSyncEngine
) : SyncLogoutFlusher {

    override suspend fun getPendingCount(): Int {
        return syncOutboxDao.getPendingCountValue() + learningOutboxDao.countByStatuses(
            listOf(
                LearningOutboxEntity.STATUS_PENDING,
                LearningOutboxEntity.STATUS_SYNCING,
                LearningOutboxEntity.STATUS_BLOCKED
            )
        )
    }

    override suspend fun drainOnce(): SyncDrainOutcome {
        val outcome = unifiedSyncEngine.drain()
        return when {
            outcome.nextRetryAtMs != null -> SyncDrainOutcome.RETRY_NEEDED
            outcome.processedAny -> SyncDrainOutcome.DRAINED
            else -> SyncDrainOutcome.EMPTY
        }
    }
}
