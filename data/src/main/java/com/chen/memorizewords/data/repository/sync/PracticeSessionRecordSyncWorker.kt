package com.chen.memorizewords.data.repository.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.chen.memorizewords.data.repository.practice.toDomain
import dagger.hilt.android.EntryPointAccessors
import java.io.IOException

class PracticeSessionRecordSyncWorker(
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

        val recordId = inputData.getLong(SyncWorkConstants.KEY_PRACTICE_SESSION_ID, -1L)
        if (recordId <= 0L) return Result.failure()

        val entity = entryPoint.appDatabase().practiceSessionRecordDao().getSessionById(recordId)
            ?: return Result.success()

        return try {
            entryPoint.remoteLearningSyncDataSource()
                .appendPracticeSession(entity.toDomain())
                .getOrThrow()
            Result.success()
        } catch (_: IOException) {
            Result.retry()
        } catch (_: Throwable) {
            Result.retry()
        }
    }
}
