package com.chen.memorizewords.data.repository.sync

import com.chen.memorizewords.data.local.room.model.sync.SyncOutboxDao
import com.chen.memorizewords.data.local.room.model.sync.SyncOutboxEntity
import com.chen.memorizewords.data.remote.HttpStatusException
import com.chen.memorizewords.data.remote.UnauthorizedException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncOutboxProcessor @Inject constructor(
    private val syncOutboxDao: SyncOutboxDao,
    private val syncOutboxStore: SyncOutboxStore,
    handlers: Set<@JvmSuppressWildcards SyncOutboxHandler>
) {

    private val handlersByBizType: Map<String, SyncOutboxHandler> =
        buildMap {
            handlers.forEach { handler ->
                handler.bizTypes.forEach { bizType ->
                    val previous = put(bizType, handler)
                    require(previous == null) {
                        "Duplicate SyncOutboxHandler for bizType=$bizType"
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
                handlersByBizType[entity.bizType]?.onSuccess(entity)
                syncOutboxDao.deleteClaimed(
                    id = entity.id,
                    leaseToken = entity.leaseToken.orEmpty()
                )
            } else {
                val throwable = result.exceptionOrNull()
                val failure = classifySyncOutboxFailure(throwable)
                val attemptTime = System.currentTimeMillis()
                if (failure.shouldRetry) {
                    syncOutboxDao.markRetryWaiting(
                        id = entity.id,
                        leaseToken = entity.leaseToken.orEmpty(),
                        lastError = failure.persistedMessage,
                        failureKind = failure.failureKind,
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
                        failureKind = failure.failureKind,
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
            ?: throw IllegalStateException("No SyncOutboxHandler for bizType=${entity.bizType}")
        handler.handle(entity)
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

internal fun classifySyncOutboxFailure(throwable: Throwable?): SyncOutboxFailureDecision {
    return when (throwable) {
        null -> SyncOutboxFailureDecision(
            shouldRetry = true,
            failureKind = SyncOutboxFailureKind.UNKNOWN,
            persistedMessage = "RETRY|unknown"
        )

        is UnauthorizedException -> SyncOutboxFailureDecision(
            shouldRetry = true,
            failureKind = SyncOutboxFailureKind.AUTH,
            persistedMessage = "RETRY|auth|${throwable.message.orEmpty()}"
        )

        is HttpStatusException -> {
            when {
                throwable.code == 429 -> SyncOutboxFailureDecision(
                    shouldRetry = true,
                    failureKind = SyncOutboxFailureKind.RATE_LIMIT,
                    persistedMessage = "RETRY|http:${throwable.code}|${throwable.message.orEmpty()}"
                )

                throwable.code >= 500 -> SyncOutboxFailureDecision(
                    shouldRetry = true,
                    failureKind = SyncOutboxFailureKind.SERVER,
                    persistedMessage = "RETRY|http:${throwable.code}|${throwable.message.orEmpty()}"
                )

                else -> SyncOutboxFailureDecision(
                    shouldRetry = false,
                    failureKind = SyncOutboxFailureKind.CLIENT,
                    persistedMessage = "TERMINAL|http:${throwable.code}|${throwable.message.orEmpty()}"
                )
            }
        }

        is IOException -> SyncOutboxFailureDecision(
            shouldRetry = true,
            failureKind = SyncOutboxFailureKind.NETWORK,
            persistedMessage = "RETRY|io|${throwable.message.orEmpty()}"
        )

        else -> SyncOutboxFailureDecision(
            shouldRetry = true,
            failureKind = SyncOutboxFailureKind.UNKNOWN,
            persistedMessage = "RETRY|unknown|${throwable.message.orEmpty()}"
        )
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

internal data class SyncOutboxFailureDecision(
    val shouldRetry: Boolean,
    val failureKind: SyncOutboxFailureKind,
    val persistedMessage: String
)
