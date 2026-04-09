package com.chen.memorizewords.data.repository.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.chen.memorizewords.domain.model.floating.FloatingWordDisplayRecord
import dagger.hilt.android.EntryPointAccessors
import java.io.IOException

class FloatingWordDisplayRecordSyncWorker(
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

        val date = inputData.getString(SyncWorkConstants.KEY_FLOATING_DATE)
        if (date.isNullOrBlank()) return Result.failure()

        val entity = entryPoint.appDatabase().floatingWordDisplayRecordDao().getByDate(date)
            ?: return Result.success()

        val record = FloatingWordDisplayRecord(
            date = entity.date,
            displayCount = entity.displayCount,
            wordIds = entity.wordIds,
            updatedAt = entity.updatedAt
        )

        return try {
            entryPoint.remoteLearningSyncDataSource()
                .upsertFloatingDisplayRecord(record)
                .getOrThrow()
            Result.success()
        } catch (_: IOException) {
            Result.retry()
        } catch (_: Throwable) {
            Result.retry()
        }
    }
}
