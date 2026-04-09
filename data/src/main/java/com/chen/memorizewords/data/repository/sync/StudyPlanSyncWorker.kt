package com.chen.memorizewords.data.repository.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.chen.memorizewords.data.remote.datasync.RemoteUserSyncDataSource
import com.chen.memorizewords.domain.auth.AuthStateProvider
import com.chen.memorizewords.domain.model.learning.LearningTestMode
import com.chen.memorizewords.domain.model.study.StudyPlan
import com.chen.memorizewords.domain.repository.WordOrderType
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.io.IOException

class StudyPlanSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext,
            StudyPlanSyncWorkerEntryPoint::class.java
        )
        if (!entryPoint.authStateProvider().isAuthenticated()) {
            return Result.success()
        }

        val dailyNewWords = inputData.getInt(SyncWorkConstants.KEY_DAILY_NEW_WORDS, -1)
        val dailyReviewCount = inputData.getInt(SyncWorkConstants.KEY_REVIEW_MULTIPLIER, -1)
        val testModeRaw = inputData.getString(SyncWorkConstants.KEY_TEST_MODE)
            ?: LearningTestMode.MEANING_CHOICE.name
        val testMode = runCatching { LearningTestMode.valueOf(testModeRaw) }
            .getOrDefault(LearningTestMode.MEANING_CHOICE)
        val orderTypeRaw = inputData.getString(SyncWorkConstants.KEY_WORD_ORDER_TYPE)
            ?: WordOrderType.RANDOM.name
        val orderType = runCatching { WordOrderType.valueOf(orderTypeRaw) }
            .getOrDefault(WordOrderType.RANDOM)
        if (dailyNewWords < 0 || dailyReviewCount <= 0) {
            return Result.failure()
        }

        return try {
            entryPoint.remoteUserSyncDataSource()
                .updateStudyPlan(
                    StudyPlan(
                        dailyNewCount = dailyNewWords,
                        dailyReviewCount = dailyReviewCount,
                        testMode = testMode,
                        wordOrderType = orderType
                    )
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
interface StudyPlanSyncWorkerEntryPoint {
    fun remoteUserSyncDataSource(): RemoteUserSyncDataSource
    fun authStateProvider(): AuthStateProvider
}
