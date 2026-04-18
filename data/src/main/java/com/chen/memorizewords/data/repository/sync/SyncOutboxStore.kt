package com.chen.memorizewords.data.repository.sync

import androidx.room.withTransaction
import com.chen.memorizewords.data.local.room.AppDatabase
import com.chen.memorizewords.data.local.room.model.sync.SyncOutboxDao
import com.chen.memorizewords.data.local.room.model.sync.SyncOutboxEntity
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncOutboxStore @Inject constructor(
    private val appDatabase: AppDatabase,
    private val syncOutboxDao: SyncOutboxDao
) {

    suspend fun enqueueLatest(
        bizType: String,
        bizKey: String,
        operation: String,
        payload: String,
        updatedAt: Long = System.currentTimeMillis()
    ) {
        appDatabase.withTransaction {
            val existing = syncOutboxDao.getByBizKey(bizKey)
            syncOutboxDao.upsert(
                buildQueuedOutboxEntity(
                    existing = existing,
                    bizType = bizType,
                    bizKey = bizKey,
                    operation = operation,
                    payload = payload,
                    updatedAt = updatedAt
                )
            )
        }
    }

    suspend fun claimNextBatch(
        limit: Int,
        now: Long = System.currentTimeMillis(),
        leaseDurationMs: Long = DEFAULT_LEASE_DURATION_MS
    ): List<SyncOutboxEntity> {
        val leaseToken = UUID.randomUUID().toString()
        return appDatabase.withTransaction {
            val claimedCount = syncOutboxDao.claimBatch(
                now = now,
                limit = limit,
                leaseToken = leaseToken,
                leaseExpiresAt = now + leaseDurationMs,
                attemptedAt = now,
                updatedAt = now
            )
            if (claimedCount <= 0) {
                return@withTransaction emptyList()
            }
            syncOutboxDao.getByLeaseToken(leaseToken)
        }
    }

    companion object {
        const val DEFAULT_LEASE_DURATION_MS: Long = 2 * 60 * 1000L
    }
}

internal fun buildQueuedOutboxEntity(
    existing: SyncOutboxEntity?,
    bizType: String,
    bizKey: String,
    operation: String,
    payload: String,
    updatedAt: Long
): SyncOutboxEntity {
    return SyncOutboxEntity(
        id = existing?.id ?: 0L,
        bizType = bizType,
        bizKey = bizKey,
        operation = operation,
        payload = payload,
        state = SyncOutboxState.QUEUED,
        retryCount = 0,
        lastError = null,
        failureKind = null,
        lastAttemptAt = 0L,
        nextRetryAt = updatedAt,
        leaseToken = null,
        leaseExpiresAt = 0L,
        updatedAt = updatedAt
    )
}
