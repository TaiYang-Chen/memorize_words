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

class WordStateSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext,
            WordStateSyncWorkerEntryPoint::class.java
        )
        if (!entryPoint.authStateProvider().isAuthenticated()) {
            return Result.success()
        }

        val bookId = inputData.getLong(SyncWorkConstants.KEY_BOOK_ID, -1L)
        val action = inputData.getString(SyncWorkConstants.KEY_WORD_STATE_ACTION)
        if (bookId <= 0L || action.isNullOrBlank()) {
            return Result.failure()
        }

        return try {
            when (action) {
                SyncWorkConstants.ACTION_UPSERT_WORD_STATE -> {
                    val wordId = inputData.getLong(SyncWorkConstants.KEY_WORD_ID, -1L)
                    val totalLearnCount = inputData.getInt(SyncWorkConstants.KEY_TOTAL_LEARN_COUNT, -1)
                    val lastLearnTime = inputData.getLong(SyncWorkConstants.KEY_LAST_LEARN_TIME, -1L)
                    val nextReviewTime = inputData.getLong(SyncWorkConstants.KEY_NEXT_REVIEW_TIME, -1L)
                    val masteryLevel = inputData.getInt(SyncWorkConstants.KEY_MASTERY_LEVEL, -1)
                    val userStatus = inputData.getInt(SyncWorkConstants.KEY_USER_STATUS, -1)
                    val repetition = inputData.getInt(SyncWorkConstants.KEY_REPETITION, -1)
                    val interval = inputData.getLong(SyncWorkConstants.KEY_INTERVAL, -1L)
                    val efactor = inputData.getDouble(SyncWorkConstants.KEY_EFACTOR, Double.NaN)

                    if (wordId <= 0L ||
                        totalLearnCount < 0 ||
                        lastLearnTime < 0L ||
                        nextReviewTime < 0L ||
                        masteryLevel < 0 ||
                        userStatus < 0 ||
                        repetition < 0 ||
                        interval < 0L ||
                        efactor.isNaN()
                    ) {
                        return Result.failure()
                    }

                    entryPoint.remoteUserSyncDataSource()
                        .upsertWordState(
                            bookId = bookId,
                            wordId = wordId,
                            totalLearnCount = totalLearnCount,
                            lastLearnTime = lastLearnTime,
                            nextReviewTime = nextReviewTime,
                            masteryLevel = masteryLevel,
                            userStatus = userStatus,
                            repetition = repetition,
                            interval = interval,
                            efactor = efactor
                        )
                        .getOrThrow()
                }

                SyncWorkConstants.ACTION_DELETE_WORD_STATES_BY_BOOK -> {
                    entryPoint.remoteUserSyncDataSource()
                        .deleteWordStatesByBookId(bookId)
                        .getOrThrow()
                }

                else -> return Result.failure()
            }
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
interface WordStateSyncWorkerEntryPoint {
    fun remoteUserSyncDataSource(): RemoteUserSyncDataSource
    fun authStateProvider(): AuthStateProvider
}
