package com.chen.memorizewords.data.sync.repository.sync

import com.chen.memorizewords.core.network.remote.HttpStatusException
import com.chen.memorizewords.core.network.remote.UnauthorizedNetworkException
import com.chen.memorizewords.domain.sync.SyncConflictPolicy
import com.chen.memorizewords.domain.sync.SyncFailureDecision
import com.chen.memorizewords.domain.sync.SyncFailureKind
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DataSyncConflictPolicy @Inject constructor() : SyncConflictPolicy {
    override fun decide(throwable: Throwable?): SyncFailureDecision {
        return classifySyncOutboxFailure(throwable)
    }
}

internal class MissingSyncOutboxHandlerException(topic: String) :
    IllegalStateException("No SyncOutboxHandler for topic=$topic")

internal class SyncEventConflictException(message: String) : IllegalStateException(message)

internal fun classifySyncOutboxFailure(throwable: Throwable?): SyncFailureDecision {
    return when (throwable) {
        null -> SyncFailureDecision(
            shouldRetry = true,
            failureKind = SyncFailureKind.UNKNOWN,
            persistedMessage = "RETRY|unknown"
        )

        is MissingSyncOutboxHandlerException -> SyncFailureDecision(
            shouldRetry = false,
            failureKind = SyncFailureKind.CLIENT,
            persistedMessage = "BLOCKED|missing_handler|${throwable.message.orEmpty()}"
        )

        is SyncEventConflictException -> SyncFailureDecision(
            shouldRetry = false,
            failureKind = SyncFailureKind.CONFLICT,
            persistedMessage = "BLOCKED|conflict|${throwable.message.orEmpty()}"
        )

        is UnauthorizedNetworkException -> SyncFailureDecision(
            shouldRetry = true,
            failureKind = SyncFailureKind.AUTH,
            persistedMessage = "RETRY|auth|${throwable.message.orEmpty()}"
        )

        is HttpStatusException -> {
            when {
                throwable.code == 409 || throwable.code == 412 -> SyncFailureDecision(
                    shouldRetry = false,
                    failureKind = SyncFailureKind.CONFLICT,
                    persistedMessage = "BLOCKED|conflict:${throwable.code}|${throwable.message.orEmpty()}"
                )

                throwable.code == 429 -> SyncFailureDecision(
                    shouldRetry = true,
                    failureKind = SyncFailureKind.RATE_LIMIT,
                    persistedMessage = "RETRY|http:${throwable.code}|${throwable.message.orEmpty()}",
                    retryAfterMillis = resolveRetryAfterMillis(throwable)
                )

                throwable.code >= 500 -> SyncFailureDecision(
                    shouldRetry = true,
                    failureKind = SyncFailureKind.SERVER,
                    persistedMessage = "RETRY|http:${throwable.code}|${throwable.message.orEmpty()}"
                )

                else -> SyncFailureDecision(
                    shouldRetry = false,
                    failureKind = SyncFailureKind.CLIENT,
                    persistedMessage = "TERMINAL|http:${throwable.code}|${throwable.message.orEmpty()}"
                )
            }
        }

        is IOException -> SyncFailureDecision(
            shouldRetry = true,
            failureKind = SyncFailureKind.NETWORK,
            persistedMessage = "RETRY|io|${throwable.message.orEmpty()}"
        )

        else -> SyncFailureDecision(
            shouldRetry = true,
            failureKind = SyncFailureKind.UNKNOWN,
            persistedMessage = "RETRY|unknown|${throwable.message.orEmpty()}"
        )
    }
}

private fun resolveRetryAfterMillis(throwable: HttpStatusException): Long? {
    throwable.retryAfterSeconds?.let { seconds ->
        return (seconds.coerceAtLeast(0L) * 1_000L)
    }
    val resetAtMs = throwable.resetAtMs ?: return null
    val referenceTime = throwable.serverTimeMs ?: System.currentTimeMillis()
    return (resetAtMs - referenceTime).coerceAtLeast(0L)
}
