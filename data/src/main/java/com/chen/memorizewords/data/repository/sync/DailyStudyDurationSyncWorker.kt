package com.chen.memorizewords.data.repository.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.chen.memorizewords.data.remote.datasync.RemoteUserSyncDataSource
import com.chen.memorizewords.domain.auth.AuthStateProvider
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.io.IOException

class DailyStudyDurationSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext,
            DailyStudyDurationSyncWorkerEntryPoint::class.java
        )
        if (!entryPoint.authStateProvider().isAuthenticated()) {
            return Result.success()
        }

        val date = inputData.getString(SyncWorkConstants.KEY_DATE)
        val totalDurationMs = inputData.getLong(SyncWorkConstants.KEY_TOTAL_DURATION_MS, -1L)
        val updatedAt = inputData.getLong(SyncWorkConstants.KEY_UPDATED_AT, -1L)
        val isNewPlanCompleted = inputData.getBoolean(SyncWorkConstants.KEY_IS_NEW_PLAN_COMPLETED, false)
        val isReviewPlanCompleted = inputData.getBoolean(SyncWorkConstants.KEY_IS_REVIEW_PLAN_COMPLETED, false)

        if (date.isNullOrBlank() || totalDurationMs < 0L || updatedAt < 0L) {
            return Result.failure()
        }

        return try {
            entryPoint.remoteUserSyncDataSource()
                .upsertDailyStudyDuration(
                    date = date,
                    totalDurationMs = totalDurationMs,
                    updatedAt = updatedAt,
                    isNewPlanCompleted = isNewPlanCompleted,
                    isReviewPlanCompleted = isReviewPlanCompleted
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

@EntryPoint
@InstallIn(SingletonComponent::class)
interface DailyStudyDurationSyncWorkerEntryPoint {
    fun remoteUserSyncDataSource(): RemoteUserSyncDataSource
    fun authStateProvider(): AuthStateProvider
}
