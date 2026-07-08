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

        while (true) {
            val learningResult = entryPoint.learningOutboxProcessor().drainBatch()
            when (learningResult) {
                SyncOutboxProcessor.DrainResult.Empty -> Unit
                SyncOutboxProcessor.DrainResult.Drained -> Unit
                SyncOutboxProcessor.DrainResult.RetryNeeded -> return Result.retry()
            }
            val globalResult = entryPoint.syncOutboxProcessor().drainBatch()
            when (globalResult) {
                SyncOutboxProcessor.DrainResult.Empty -> {
                    if (learningResult == SyncOutboxProcessor.DrainResult.Empty) {
                        return Result.success()
                    }
                }
                SyncOutboxProcessor.DrainResult.Drained -> Unit
                SyncOutboxProcessor.DrainResult.RetryNeeded -> return Result.retry()
            }
        }
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface SyncOutboxDrainWorkerEntryPoint {
    fun learningOutboxProcessor(): LearningOutboxProcessor
    fun syncOutboxProcessor(): SyncOutboxProcessor
    fun authStateProvider(): AuthStateProvider
}
