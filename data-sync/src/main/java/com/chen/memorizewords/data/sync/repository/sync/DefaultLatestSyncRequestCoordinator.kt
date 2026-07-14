package com.chen.memorizewords.data.sync.repository.sync

import com.chen.memorizewords.core.network.http.NetworkResult
import com.chen.memorizewords.data.sync.local.room.model.sync.FailedSyncDeliveryMode
import com.chen.memorizewords.data.sync.local.room.model.sync.FailedSyncEventEntity
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
class DefaultLatestSyncRequestCoordinator @Inject constructor(
    private val eventAccess: LatestSyncEventAccess,
    private val sessionGate: FailureQueueSessionGate
) : LatestSyncRequestCoordinator {
    private val mutexes = Array(MUTEX_STRIPES) { Mutex() }
    private val resolvedLatest = ConcurrentHashMap.newKeySet<LatestSyncIdentity>()

    override suspend fun <T> executeNormal(
        latestDedupeKey: String?,
        block: suspend () -> NetworkResult<T>
    ): NetworkResult<T> {
        val dedupeKey = latestDedupeKey?.takeIf(String::isNotBlank) ?: return block()
        val sessionToken = sessionGate.capture()
        val identity = LatestSyncIdentity(dedupeKey)
        return mutex(identity).withLock {
            val result = block()
            if (
                result is NetworkResult.Success &&
                sessionToken != null &&
                sessionGate.isCurrent(sessionToken)
            ) {
                resolvedLatest += identity
            } else {
                resolvedLatest -= identity
            }
            result
        }
    }

    override suspend fun replay(
        event: FailedSyncEventEntity,
        block: suspend () -> ReplayOutcome
    ): ReplayOutcome {
        val dedupeKey = event.dedupeKey
        if (event.deliveryMode != FailedSyncDeliveryMode.LATEST || dedupeKey.isNullOrBlank()) {
            return block()
        }
        val identity = LatestSyncIdentity(dedupeKey)
        return mutex(identity).withLock {
            if (identity in resolvedLatest || !eventAccess.isClaimed(event)) {
                ReplayOutcome.Success
            } else {
                block()
            }
        }
    }

    override fun onSessionInvalidated() {
        resolvedLatest.clear()
    }

    private fun mutex(identity: LatestSyncIdentity): Mutex {
        val hash = identity.dedupeKey.hashCode()
        return mutexes[(hash and Int.MAX_VALUE) % mutexes.size]
    }

    private companion object {
        const val MUTEX_STRIPES = 64
    }
}
