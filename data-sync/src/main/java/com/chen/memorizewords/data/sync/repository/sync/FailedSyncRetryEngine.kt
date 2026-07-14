package com.chen.memorizewords.data.sync.repository.sync

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
class FailedSyncRetryEngine @Inject constructor(
    private val sessionGate: FailureQueueSessionGate,
    private val store: FailedSyncEventStore,
    private val replayer: FailedSyncEventReplayer,
    private val pendingSignal: FailedEventPendingSignal
) {
    private val mutex = Mutex()

    suspend fun drain(recovery: Boolean = false): DrainOutcome = mutex.withLock {
        val sessionToken = sessionGate.capture()
            ?: return@withLock DrainOutcome(null, false, false)
        if (recovery) store.resumeRetryWaiting()
        var rounds = 0
        var reachedLimit = false
        var sessionInvalidated = false
        var networkFailed = false
        while (rounds < MAX_ROUNDS) {
            if (!sessionGate.isCurrent(sessionToken)) {
                sessionInvalidated = true
                break
            }
            rounds++
            val batch = store.claimDue()
            if (batch.isEmpty()) break
            try {
                for (entity in batch) {
                    if (!sessionGate.isCurrent(sessionToken)) {
                        sessionInvalidated = true
                        break
                    }
                    when (val outcome = replayer.replay(entity, sessionToken)) {
                        ReplayOutcome.Success -> if (sessionGate.isCurrent(sessionToken)) {
                            store.markSucceeded(entity)
                        }
                        is ReplayOutcome.NetworkFailure -> if (sessionGate.isCurrent(sessionToken)) {
                            val nextAttemptAtMs = store.markNetworkFailure(entity, outcome.message)
                            entity.leaseToken?.let { leaseToken ->
                                // A transport failure is shared infrastructure evidence. Defer the
                                // rest of this claimed batch without incrementing their attempts,
                                // rather than paying another timeout for every queued event.
                                store.releaseLease(leaseToken, nextAttemptAtMs)
                            }
                            networkFailed = true
                            break
                        }
                        is ReplayOutcome.Blocked -> if (sessionGate.isCurrent(sessionToken)) {
                            store.markBlocked(entity, outcome.message)
                        }
                        ReplayOutcome.SessionInvalidated -> sessionInvalidated = true
                    }
                }
            } catch (cancelled: CancellationException) {
                val leaseToken = batch.firstOrNull()?.leaseToken
                if (leaseToken != null) {
                    withContext(NonCancellable) { store.releaseLease(leaseToken) }
                }
                throw cancelled
            }
            if (sessionInvalidated || networkFailed) break
            reachedLimit = rounds == MAX_ROUNDS
        }
        if (sessionGate.isCurrent(sessionToken)) {
            val signalVersion = pendingSignal.snapshotVersion()
            if (store.retryableCount() == 0) {
                pendingSignal.clearIfUnchanged(signalVersion)
            }
        }
        val sessionIsCurrent = sessionGate.isCurrent(sessionToken)
        val hasImmediateWork = sessionIsCurrent && store.hasClaimable()
        DrainOutcome(
            nextRetryAtMs = if (sessionIsCurrent) store.earliestRetryAt() else null,
            reachedWorkLimit = reachedLimit && hasImmediateWork,
            hasImmediateWork = hasImmediateWork
        )
    }

    data class DrainOutcome(
        val nextRetryAtMs: Long?,
        val reachedWorkLimit: Boolean,
        val hasImmediateWork: Boolean
    )

    private companion object {
        const val MAX_ROUNDS = 12
    }
}
