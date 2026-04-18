package com.chen.memorizewords.data.repository.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.chen.memorizewords.domain.auth.AuthStateProvider
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

        while (true) {
            when (entryPoint.syncOutboxProcessor().drainBatch()) {
                SyncOutboxProcessor.DrainResult.Empty -> return Result.success()
                SyncOutboxProcessor.DrainResult.Drained -> Unit
                SyncOutboxProcessor.DrainResult.RetryNeeded -> return Result.retry()
            }
        }
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface SyncOutboxDrainWorkerEntryPoint {
    fun syncOutboxProcessor(): SyncOutboxProcessor
    fun authStateProvider(): AuthStateProvider
}
