package com.chen.memorizewords.data.sync.repository.sync

import com.chen.memorizewords.data.sync.local.room.model.sync.FailedSyncEventDao
import com.chen.memorizewords.domain.sync.SyncDrainOutcome
import com.chen.memorizewords.domain.sync.SyncLogoutFlusher
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DataSyncLogoutFlusher @Inject constructor(
    private val failedSyncEventDao: FailedSyncEventDao,
    private val retryEngine: FailedSyncRetryEngine
) : SyncLogoutFlusher {

    override suspend fun getPendingCount(): Int {
        return failedSyncEventDao.pendingCount()
    }

    override suspend fun drainOnce(): SyncDrainOutcome {
        val before = getPendingCount()
        if (before <= 0) return SyncDrainOutcome.EMPTY
        val outcome = retryEngine.drain(recovery = true)
        val after = getPendingCount()
        return when {
            outcome.nextRetryAtMs != null -> SyncDrainOutcome.RETRY_NEEDED
            after < before -> SyncDrainOutcome.DRAINED
            else -> SyncDrainOutcome.EMPTY
        }
    }
}
