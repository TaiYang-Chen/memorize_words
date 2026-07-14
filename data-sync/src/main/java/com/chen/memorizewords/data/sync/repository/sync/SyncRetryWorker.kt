package com.chen.memorizewords.data.sync.repository.sync

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException

class SyncRetryWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        return try {
            val entryPoint = EntryPointAccessors.fromApplication(
                applicationContext,
                SyncRetryWorkerEntryPoint::class.java
            )
            val outcome = entryPoint.retryEngine().drain(
                recovery = inputData.getBoolean(KEY_RECOVERY, false)
            )
            Log.d(
                TAG,
                "failure_queue drain_complete nextRetryAtMs=${outcome.nextRetryAtMs} " +
                    "reachedWorkLimit=${outcome.reachedWorkLimit} " +
                    "hasImmediateWork=${outcome.hasImmediateWork}"
            )
            outcome.nextRetryAtMs?.let(entryPoint.scheduler()::scheduleRetryAt)
            if (outcome.reachedWorkLimit || outcome.hasImmediateWork) {
                entryPoint.scheduler().scheduleContinuation()
            }
            Result.success()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            Log.e(TAG, "failure_queue worker_failed", failure)
            Result.retry()
        }
    }

    companion object {
        const val KEY_RECOVERY = "failed_sync_recovery"
        private const val TAG = "SyncRetryWorker"
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface SyncRetryWorkerEntryPoint {
    fun retryEngine(): FailedSyncRetryEngine
    fun scheduler(): FailedSyncScheduler
}
