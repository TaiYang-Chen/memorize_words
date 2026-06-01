package com.chen.memorizewords.data.sync.repository.sync

import com.chen.memorizewords.data.sync.local.room.model.sync.SyncOutboxDao
import com.chen.memorizewords.domain.sync.SyncDrainOutcome
import com.chen.memorizewords.domain.sync.SyncLogoutFlusher
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DataSyncLogoutFlusher @Inject constructor(
    private val syncOutboxDao: SyncOutboxDao,
    private val syncOutboxProcessor: SyncOutboxProcessor
) : SyncLogoutFlusher {

    override suspend fun getPendingCount(): Int {
        return syncOutboxDao.getPendingCountValue()
    }

    override suspend fun drainOnce(): SyncDrainOutcome {
        return when (syncOutboxProcessor.drainBatch()) {
            SyncOutboxProcessor.DrainResult.Empty -> SyncDrainOutcome.EMPTY
            SyncOutboxProcessor.DrainResult.Drained -> SyncDrainOutcome.DRAINED
            SyncOutboxProcessor.DrainResult.RetryNeeded -> SyncDrainOutcome.RETRY_NEEDED
        }
    }
}
