package com.chen.memorizewords.data.repository.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.chen.memorizewords.domain.model.practice.PracticeSettings
import dagger.hilt.android.EntryPointAccessors
import java.io.IOException

class PracticeSettingsSyncWorker(
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

        val selectedBookId = inputData.getLong(SyncWorkConstants.KEY_PRACTICE_SELECTED_BOOK_ID, 0L)
        val intervalSeconds = inputData.getInt(SyncWorkConstants.KEY_PRACTICE_INTERVAL_SECONDS, 0)
        val loopEnabled = inputData.getBoolean(SyncWorkConstants.KEY_PRACTICE_LOOP_ENABLED, true)
        val playWordSpelling =
            inputData.getBoolean(SyncWorkConstants.KEY_PRACTICE_PLAY_WORD_SPELLING, true)
        val playChineseMeaning =
            inputData.getBoolean(SyncWorkConstants.KEY_PRACTICE_PLAY_CHINESE_MEANING, false)

        val settings = PracticeSettings(
            selectedBookId = selectedBookId,
            intervalSeconds = intervalSeconds.coerceAtLeast(1),
            loopEnabled = loopEnabled,
            playWordSpelling = playWordSpelling,
            playChineseMeaning = playChineseMeaning
        )

        return try {
            entryPoint.remoteLearningSyncDataSource()
                .updatePracticeSettings(settings)
                .getOrThrow()
            Result.success()
        } catch (_: IOException) {
            Result.retry()
        } catch (_: Throwable) {
            Result.retry()
        }
    }
}
