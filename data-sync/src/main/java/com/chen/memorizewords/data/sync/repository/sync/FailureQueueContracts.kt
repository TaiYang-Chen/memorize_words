package com.chen.memorizewords.data.sync.repository.sync

import com.chen.memorizewords.core.network.http.NetworkResult
import com.chen.memorizewords.data.sync.local.room.model.sync.FailedSyncEventEntity
import java.io.IOException
import retrofit2.Call

interface FailedRequestRecorder {
    suspend fun record(
        call: Call<*>,
        failure: IOException,
        sessionToken: FailureQueueSessionToken
    ): FailureRecordResult
}

enum class FailureRecordResult {
    RECORDED,
    SKIPPED_SESSION_INVALID,
    SKIPPED_NO_ANNOTATION,
    SKIPPED_NO_INVOCATION,
    FAILED_TO_PERSIST
}

interface FailedSyncScheduler {
    fun scheduleDrain()
    fun scheduleContinuation()
    fun scheduleRecovery()
    fun scheduleRetryAt(nextAttemptAtMs: Long)
    fun ensurePeriodic()
    fun cancelAll()
}

interface NetworkRecoveryNotifier {
    fun onNormalRequestSucceeded()
    fun onNetworkAvailable() = onNormalRequestSucceeded()
    fun onSessionInvalidated()
}

interface LatestSyncRequestCoordinator {
    suspend fun <T> executeNormal(
        latestDedupeKey: String?,
        block: suspend () -> NetworkResult<T>
    ): NetworkResult<T>

    suspend fun replay(
        event: FailedSyncEventEntity,
        block: suspend () -> ReplayOutcome
    ): ReplayOutcome

    fun onSessionInvalidated()
}

interface LatestSyncEventAccess {
    suspend fun isClaimed(event: FailedSyncEventEntity): Boolean
}
