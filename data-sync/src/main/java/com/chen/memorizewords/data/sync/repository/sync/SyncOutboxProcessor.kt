package com.chen.memorizewords.data.sync.repository.sync

import com.chen.memorizewords.data.sync.local.room.model.sync.SyncOutboxDao
import com.chen.memorizewords.data.sync.local.room.model.sync.SyncOutboxEntity
import com.chen.memorizewords.domain.sync.SyncConflictPolicy
import com.chen.memorizewords.domain.sync.SyncOutboxHandler as DomainSyncOutboxHandler
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncOutboxProcessor @Inject constructor(
    private val syncOutboxDao: SyncOutboxDao,
    private val syncOutboxStore: SyncOutboxStore,
    private val conflictPolicy: SyncConflictPolicy,
    handlers: Set<@JvmSuppressWildcards DomainSyncOutboxHandler>
) {

    private val handlersByBizType: Map<String, DomainSyncOutboxHandler> =
        buildMap {
            handlers.forEach { handler ->
                handler.topics.forEach { topic ->
                    val previous = put(topic, handler)
                    require(previous == null) {
                        "Duplicate SyncOutboxHandler for topic=$topic"
                    }
                }
            }
        }

    suspend fun drainBatch(limit: Int = DEFAULT_BATCH_SIZE): DrainResult {
        val batch = syncOutboxStore.claimNextBatch(limit)
        if (batch.isEmpty()) {
            return DrainResult.Empty
        }

        var shouldRetry = false
        batch.forEach { entity ->
            val result = runCatching { dispatch(entity) }
            if (result.isSuccess) {
                handlersByBizType[entity.bizType]?.onSuccess(entity.toDomainRecord())
                syncOutboxDao.deleteClaimed(
                    id = entity.id,
                    leaseToken = entity.leaseToken.orEmpty()
                )
            } else {
                val throwable = result.exceptionOrNull()
                val failure = conflictPolicy.decide(throwable)
                val attemptTime = System.currentTimeMillis()
                if (failure.shouldRetry) {
                    syncOutboxDao.markRetryWaiting(
                        id = entity.id,
                        leaseToken = entity.leaseToken.orEmpty(),
                        lastError = failure.persistedMessage,
                        failureKind = failure.failureKind.toDataFailureKind(),
                        lastAttemptAt = attemptTime,
                        nextRetryAt = attemptTime + syncOutboxBackoffDelayMillis(entity.retryCount + 1),
                        updatedAt = attemptTime
                    )
                    shouldRetry = true
                } else {
                    syncOutboxDao.markBlocked(
                        id = entity.id,
                        leaseToken = entity.leaseToken.orEmpty(),
                        lastError = failure.persistedMessage,
                        failureKind = failure.failureKind.toDataFailureKind(),
                        lastAttemptAt = attemptTime,
                        updatedAt = attemptTime
                    )
                }
            }
        }

        return if (shouldRetry) DrainResult.RetryNeeded else DrainResult.Drained
    }

    private suspend fun dispatch(entity: SyncOutboxEntity) {
        val handler = handlersByBizType[entity.bizType]
            ?: throw MissingSyncOutboxHandlerException(entity.bizType)
        handler.handle(entity.toDomainRecord())
    }

    sealed interface DrainResult {
        data object Empty : DrainResult
        data object Drained : DrainResult
        data object RetryNeeded : DrainResult
    }

    companion object {
        private const val DEFAULT_BATCH_SIZE = 20
    }
}

private fun com.chen.memorizewords.domain.sync.SyncFailureKind.toDataFailureKind(): SyncOutboxFailureKind {
    return when (this) {
        com.chen.memorizewords.domain.sync.SyncFailureKind.NETWORK -> SyncOutboxFailureKind.NETWORK
        com.chen.memorizewords.domain.sync.SyncFailureKind.AUTH -> SyncOutboxFailureKind.AUTH
        com.chen.memorizewords.domain.sync.SyncFailureKind.SERVER -> SyncOutboxFailureKind.SERVER
        com.chen.memorizewords.domain.sync.SyncFailureKind.RATE_LIMIT -> SyncOutboxFailureKind.RATE_LIMIT
        com.chen.memorizewords.domain.sync.SyncFailureKind.CLIENT -> SyncOutboxFailureKind.CLIENT
        com.chen.memorizewords.domain.sync.SyncFailureKind.CONFLICT -> SyncOutboxFailureKind.CONFLICT
        com.chen.memorizewords.domain.sync.SyncFailureKind.UNKNOWN -> SyncOutboxFailureKind.UNKNOWN
    }
}

internal fun syncOutboxBackoffDelayMillis(attemptNumber: Int): Long {
    return when (attemptNumber.coerceAtLeast(1)) {
        1 -> 30_000L
        2 -> 2 * 60_000L
        3 -> 10 * 60_000L
        else -> 30 * 60_000L
    }
}
