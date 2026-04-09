package com.chen.memorizewords.data.repository.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.hilt.android.EntryPointAccessors
import java.io.IOException

class PracticeDurationSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext,
            LearningSyncWorkerEntryPoint::class.java
        )
        if (!entryPoint.authStateProvider().isAuthenticated()) {
            return Result.success()
        }

        val date = inputData.getString(SyncWorkConstants.KEY_PRACTICE_DATE)
        if (date.isNullOrBlank()) return Result.failure()

        val duration = entryPoint.appDatabase().dailyPracticeDurationDao().getByDate(date)
            ?: return Result.success()

        return try {
            entryPoint.remoteLearningSyncDataSource()
                .upsertPracticeDuration(
                    date = duration.date,
                    totalDurationMs = duration.totalDurationMs,
                    updatedAt = duration.updatedAt
                )
                .getOrThrow()
            Result.success()
        } catch (_: IOException) {
            Result.retry()
        } catch (_: Throwable) {
            Result.retry()
        }
    }
}
