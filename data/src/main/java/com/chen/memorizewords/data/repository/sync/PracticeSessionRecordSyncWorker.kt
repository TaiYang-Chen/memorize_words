package com.chen.memorizewords.data.repository.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.chen.memorizewords.domain.model.practice.PracticeEntryType
import com.chen.memorizewords.domain.model.practice.PracticeMode
import com.chen.memorizewords.domain.model.practice.PracticeSessionRecord
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

        val mode = runCatching { PracticeMode.valueOf(entity.mode) }
            .getOrDefault(PracticeMode.LISTENING)
        val entryType = runCatching { PracticeEntryType.valueOf(entity.entryType) }
            .getOrDefault(PracticeEntryType.RANDOM)

        val record = PracticeSessionRecord(
            id = entity.id,
            date = entity.date,
            mode = mode,
            entryType = entryType,
            entryCount = entity.entryCount,
            durationMs = entity.durationMs,
            createdAt = entity.createdAt,
            wordIds = entity.wordIds,
            questionCount = entity.questionCount,
            completedCount = entity.completedCount,
            correctCount = entity.correctCount,
            submitCount = entity.submitCount
        )

        return try {
            entryPoint.remoteLearningSyncDataSource()
                .appendPracticeSession(record)
                .getOrThrow()
            Result.success()
        } catch (_: IOException) {
            Result.retry()
        } catch (_: Throwable) {
            Result.retry()
        }
    }
}
