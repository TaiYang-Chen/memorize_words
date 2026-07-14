package com.chen.memorizewords.data.sync.repository.sync

import androidx.room.withTransaction
import com.chen.memorizewords.data.sync.local.room.SyncDatabase
import com.chen.memorizewords.data.sync.local.room.model.sync.FailedSyncDeliveryMode
import com.chen.memorizewords.data.sync.local.room.model.sync.FailedSyncEventDao
import com.chen.memorizewords.data.sync.local.room.model.sync.FailedSyncEventEntity
import com.chen.memorizewords.data.sync.local.room.model.sync.FailedSyncState
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

@Singleton
class FailedSyncEventStore @Inject constructor(
    private val database: SyncDatabase,
    private val dao: FailedSyncEventDao
) : LatestSyncEventAccess {
    suspend fun save(event: FailedSyncEvent) {
        val now = System.currentTimeMillis()
        database.withTransaction {
            val mergeable = if (
                event.deliveryMode == FailedSyncDeliveryMode.LATEST &&
                !event.dedupeKey.isNullOrBlank()
            ) {
                dao.findMergeable(event.dedupeKey)
            } else {
                null
            }
            if (mergeable != null) {
                val replaced = dao.replaceMergeable(
                    eventId = mergeable.eventId,
                    eventType = event.eventType,
                    schemaVersion = event.schemaVersion,
                    deliveryMode = event.deliveryMode,
                    orderingKey = event.orderingKey,
                    sequence = event.sequence,
                    paramsJson = event.paramsJson,
                    occurredAtMs = event.occurredAtMs,
                    now = now
                )
                if (replaced > 0) return@withTransaction
            }
            dao.insert(event.toEntity(now))
        }
    }

    suspend fun claimDue(
        limit: Int = DEFAULT_BATCH_SIZE,
        now: Long = System.currentTimeMillis()
    ): List<FailedSyncEventEntity> {
        val leaseToken = UUID.randomUUID().toString()
        return database.withTransaction {
            val count = dao.claimDue(
                limit = limit,
                leaseToken = leaseToken,
                leaseExpiresAtMs = now + LEASE_DURATION_MS,
                now = now
            )
            if (count <= 0) emptyList() else dao.getByLeaseToken(leaseToken)
        }
    }

    suspend fun resumeRetryWaiting(now: Long = System.currentTimeMillis()) {
        dao.resumeRetryWaiting(now)
    }

    suspend fun retryableCount(): Int = dao.retryableCount()

    suspend fun hasClaimable(now: Long = System.currentTimeMillis()): Boolean =
        dao.hasClaimable(now)

    suspend fun earliestRetryAt(now: Long = System.currentTimeMillis()): Long? =
        dao.earliestRetryAt(now)

    suspend fun markSucceeded(entity: FailedSyncEventEntity) {
        dao.deleteClaimed(entity.eventId, entity.leaseToken.orEmpty())
    }

    override suspend fun isClaimed(event: FailedSyncEventEntity): Boolean {
        val leaseToken = event.leaseToken ?: return false
        return dao.isClaimed(event.eventId, leaseToken)
    }

    suspend fun markNetworkFailure(
        entity: FailedSyncEventEntity,
        message: String,
        now: Long = System.currentTimeMillis()
    ): Long {
        val nextAttemptAtMs = now + failedSyncBackoffDelayMillis(entity.attemptCount + 1)
        dao.markRetryWaiting(
            eventId = entity.eventId,
            leaseToken = entity.leaseToken.orEmpty(),
            lastError = message.take(MAX_ERROR_LENGTH),
            nextAttemptAtMs = nextAttemptAtMs,
            now = now
        )
        return nextAttemptAtMs
    }

    suspend fun markBlocked(
        entity: FailedSyncEventEntity,
        message: String,
        now: Long = System.currentTimeMillis()
    ) {
        dao.markBlocked(
            eventId = entity.eventId,
            leaseToken = entity.leaseToken.orEmpty(),
            lastError = message.take(MAX_ERROR_LENGTH),
            now = now
        )
    }

    suspend fun releaseLease(
        leaseToken: String,
        nextAttemptAtMs: Long = System.currentTimeMillis(),
        now: Long = System.currentTimeMillis()
    ) {
        if (leaseToken.isNotBlank()) dao.releaseLease(leaseToken, nextAttemptAtMs, now)
    }

    private fun FailedSyncEvent.toEntity(now: Long): FailedSyncEventEntity {
        return FailedSyncEventEntity(
            eventId = eventId,
            eventType = eventType,
            schemaVersion = schemaVersion,
            deliveryMode = deliveryMode,
            dedupeKey = dedupeKey,
            orderingKey = orderingKey,
            sequence = sequence,
            paramsJson = paramsJson,
            state = initialState,
            attemptCount = 0,
            lastError = initialError?.take(MAX_ERROR_LENGTH),
            nextAttemptAtMs = now,
            leaseToken = null,
            leaseExpiresAtMs = 0L,
            occurredAtMs = occurredAtMs,
            createdAtMs = now,
            updatedAtMs = now
        )
    }

    companion object {
        const val DEFAULT_BATCH_SIZE = 20
        const val LEASE_DURATION_MS = 2 * 60_000L
        const val MAX_PARAMS_BYTES = 64 * 1024
        private const val MAX_ERROR_LENGTH = 1_000
    }
}

internal fun failedSyncBackoffDelayMillis(
    attemptNumber: Int,
    jitterFactor: Double = Random.Default.nextDouble(from = 0.5, until = 1.5)
): Long {
    val baseDelay = when (attemptNumber.coerceAtLeast(1)) {
        1 -> 30_000L
        2 -> 2 * 60_000L
        3 -> 10 * 60_000L
        else -> 30 * 60_000L
    }
    return (baseDelay * jitterFactor.coerceIn(0.5, 1.5)).toLong()
}
