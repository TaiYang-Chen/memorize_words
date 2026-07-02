package com.chen.memorizewords.data.sync.repository.sync

import androidx.room.withTransaction
import com.chen.memorizewords.data.sync.local.room.SyncDatabase
import com.chen.memorizewords.data.sync.local.room.model.sync.SyncOutboxDao
import com.chen.memorizewords.data.sync.local.room.model.sync.SyncOutboxEntity
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncOutboxStore @Inject constructor(
    private val syncDatabase: SyncDatabase,
    private val syncOutboxDao: SyncOutboxDao
) : SyncOutboxRetryWaitResumer {

    suspend fun enqueueLatest(
        bizType: String,
        bizKey: String,
        operation: SyncOutboxOperation,
        payload: String,
        updatedAt: Long = System.currentTimeMillis()
    ) {
        syncDatabase.withTransaction {
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

    suspend fun enqueueLatest(commands: List<SyncOutboxWriteCommand>) {
        if (commands.isEmpty()) return
        syncDatabase.withTransaction {
            val existingByBizKey = commands.map { it.bizKey }
                .distinct()
                .chunked(SQL_BIND_CHUNK_SIZE)
                .flatMap { keys -> syncOutboxDao.getByBizKeys(keys) }
                .associateBy { it.bizKey }
            val entities = buildQueuedOutboxEntities(commands, existingByBizKey)
            syncOutboxDao.upsertAll(entities)
        }
    }

    suspend fun claimNextBatch(
        limit: Int,
        now: Long = System.currentTimeMillis(),
        leaseDurationMs: Long = DEFAULT_LEASE_DURATION_MS
    ): List<SyncOutboxEntity> {
        val leaseToken = UUID.randomUUID().toString()
        return syncDatabase.withTransaction {
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

    override suspend fun resumeRetryWaiting(now: Long) {
        syncOutboxDao.resumeRetryWaiting(now)
    }

    companion object {
        const val DEFAULT_LEASE_DURATION_MS: Long = 2 * 60 * 1000L
    }
}

data class SyncOutboxWriteCommand(
    val bizType: String,
    val bizKey: String,
    val operation: SyncOutboxOperation,
    val payload: String,
    val updatedAt: Long
)

interface SyncOutboxRetryWaitResumer {
    suspend fun resumeRetryWaiting(now: Long = System.currentTimeMillis())
}

private const val SQL_BIND_CHUNK_SIZE = 500

internal fun buildQueuedOutboxEntity(
    existing: SyncOutboxEntity?,
    bizType: String,
    bizKey: String,
    operation: SyncOutboxOperation,
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

internal fun buildQueuedOutboxEntities(
    commands: List<SyncOutboxWriteCommand>,
    existingByBizKey: Map<String, SyncOutboxEntity>
): List<SyncOutboxEntity> {
    return commands.map { command ->
        buildQueuedOutboxEntity(
            existing = existingByBizKey[command.bizKey],
            bizType = command.bizType,
            bizKey = command.bizKey,
            operation = command.operation,
            payload = command.payload,
            updatedAt = command.updatedAt
        )
    }
}
