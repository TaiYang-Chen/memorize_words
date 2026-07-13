package com.chen.memorizewords.data.sync.repository.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.chen.memorizewords.domain.account.auth.AuthStateProvider
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

class SyncOutboxDrainWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext,
            SyncOutboxDrainWorkerEntryPoint::class.java
        )

        if (!entryPoint.authStateProvider().isAuthenticated()) {
            return Result.success()
        }

        val outcome = entryPoint.unifiedSyncEngine().drain()
        outcome.nextRetryAtMs?.let { nextRetryAt ->
            entryPoint.syncOutboxDrainScheduler().scheduleRetryAt(
                nextAttemptAtMs = nextRetryAt
            )
        }
        if (outcome.reachedWorkLimit) {
            entryPoint.syncOutboxDrainScheduler().scheduleRetryAt(
                nextAttemptAtMs = System.currentTimeMillis()
            )
        }
        return Result.success()
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface SyncOutboxDrainWorkerEntryPoint {
    fun unifiedSyncEngine(): UnifiedSyncEngine
    fun syncOutboxDrainScheduler(): SyncOutboxDrainScheduler
    fun authStateProvider(): AuthStateProvider
}
