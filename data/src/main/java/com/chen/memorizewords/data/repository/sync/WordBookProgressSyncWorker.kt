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

class WordBookProgressSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext,
            WordBookProgressSyncWorkerEntryPoint::class.java
        )
        if (!entryPoint.authStateProvider().isAuthenticated()) {
            return Result.success()
        }

        val bookId = inputData.getLong(SyncWorkConstants.KEY_BOOK_ID, -1L)
        val bookName = inputData.getString(SyncWorkConstants.KEY_BOOK_NAME)
        val learnedCount = inputData.getInt(SyncWorkConstants.KEY_LEARNED_COUNT, -1)
        val masteredCount = inputData.getInt(SyncWorkConstants.KEY_MASTERED_COUNT, -1)
        val totalCount = inputData.getInt(SyncWorkConstants.KEY_TOTAL_COUNT, -1)
        val correctCount = inputData.getInt(SyncWorkConstants.KEY_CORRECT_COUNT, -1)
        val wrongCount = inputData.getInt(SyncWorkConstants.KEY_WRONG_COUNT, -1)
        val studyDayCount = inputData.getInt(SyncWorkConstants.KEY_STUDY_DAY_COUNT, -1)
        val lastStudyDate = inputData.getString(SyncWorkConstants.KEY_LAST_STUDY_DATE)

        if (bookId <= 0L ||
            bookName.isNullOrBlank() ||
            learnedCount < 0 ||
            masteredCount < 0 ||
            totalCount < 0 ||
            correctCount < 0 ||
            wrongCount < 0 ||
            studyDayCount < 0 ||
            lastStudyDate.isNullOrBlank()
        ) {
            return Result.failure()
        }

        return try {
            entryPoint.remoteUserSyncDataSource()
                .upsertWordBookProgress(
                    bookId = bookId,
                    bookName = bookName,
                    learnedCount = learnedCount,
                    masteredCount = masteredCount,
                    totalCount = totalCount,
                    correctCount = correctCount,
                    wrongCount = wrongCount,
                    studyDayCount = studyDayCount,
                    lastStudyDate = lastStudyDate
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
interface WordBookProgressSyncWorkerEntryPoint {
    fun remoteUserSyncDataSource(): RemoteUserSyncDataSource
    fun authStateProvider(): AuthStateProvider
}
