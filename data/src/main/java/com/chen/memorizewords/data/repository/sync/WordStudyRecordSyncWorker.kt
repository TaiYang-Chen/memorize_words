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

class WordStudyRecordSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext,
            WordStudyRecordSyncWorkerEntryPoint::class.java
        )
        if (!entryPoint.authStateProvider().isAuthenticated()) {
            return Result.success()
        }

        val date = inputData.getString(SyncWorkConstants.KEY_DATE)
        val wordId = inputData.getLong(SyncWorkConstants.KEY_WORD_ID, -1L)
        val word = inputData.getString(SyncWorkConstants.KEY_WORD)
        val definition = inputData.getString(SyncWorkConstants.KEY_DEFINITION)
        val isNewWord = inputData.getBoolean(SyncWorkConstants.KEY_IS_NEW_WORD, false)

        if (date.isNullOrBlank() || wordId <= 0L || word.isNullOrBlank() || definition.isNullOrBlank()) {
            return Result.failure()
        }

        return try {
            entryPoint.remoteUserSyncDataSource()
                .appendStudyRecord(
                    date = date,
                    wordId = wordId,
                    word = word,
                    definition = definition,
                    isNewWord = isNewWord
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
interface WordStudyRecordSyncWorkerEntryPoint {
    fun remoteUserSyncDataSource(): RemoteUserSyncDataSource
    fun authStateProvider(): AuthStateProvider
}
