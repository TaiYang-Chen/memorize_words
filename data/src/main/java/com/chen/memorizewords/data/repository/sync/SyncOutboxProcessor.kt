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
        val batch = syncOutboxDao.getNextBatch(limit)
        if (batch.isEmpty()) {
            return DrainResult.Empty
        }

        val now = System.currentTimeMillis()
        syncOutboxDao.markSyncing(batch.map { it.id }, now)

        var shouldRetry = false
        batch.forEach { entity ->
            val result = runCatching { dispatch(entity) }
            if (result.isSuccess) {
                handlersByBizType[entity.bizType]?.onSuccess(entity)
                syncOutboxDao.deleteByIds(listOf(entity.id))
            } else {
                val throwable = result.exceptionOrNull()
                val failure = classifyFailure(throwable)
                syncOutboxDao.markFailed(
                    id = entity.id,
                    lastError = failure.persistedMessage,
                    updatedAt = System.currentTimeMillis()
                )
                if (failure.shouldRetry) {
                    shouldRetry = true
                }
            }
        }

        return if (shouldRetry) DrainResult.RetryNeeded else DrainResult.Drained
    }

    private suspend fun dispatch(entity: SyncOutboxEntity) {
        val handler = handlersByBizType[entity.bizType] ?: return
        handler.handle(entity)
    }

    private fun classifyFailure(throwable: Throwable?): FailureDecision {
        return when (throwable) {
            null -> FailureDecision(
                shouldRetry = true,
                persistedMessage = "RETRY|unknown"
            )

            is UnauthorizedException -> FailureDecision(
                shouldRetry = true,
                persistedMessage = "RETRY|auth|${throwable.message.orEmpty()}"
            )

            is HttpStatusException -> {
                if (throwable.code >= 500 || throwable.code == 429) {
                    FailureDecision(
                        shouldRetry = true,
                        persistedMessage = "RETRY|http:${throwable.code}|${throwable.message.orEmpty()}"
                    )
                } else {
                    FailureDecision(
                        shouldRetry = false,
                        persistedMessage = "TERMINAL|http:${throwable.code}|${throwable.message.orEmpty()}"
                    )
                }
            }

            is IOException -> FailureDecision(
                shouldRetry = true,
                persistedMessage = "RETRY|io|${throwable.message.orEmpty()}"
            )

            else -> FailureDecision(
                shouldRetry = true,
                persistedMessage = "RETRY|unknown|${throwable.message.orEmpty()}"
            )
        }
    }

    private data class FailureDecision(
        val shouldRetry: Boolean,
        val persistedMessage: String
    )

    sealed interface DrainResult {
        data object Empty : DrainResult
        data object Drained : DrainResult
        data object RetryNeeded : DrainResult
    }

    companion object {
        private const val DEFAULT_BATCH_SIZE = 20
    }
}
