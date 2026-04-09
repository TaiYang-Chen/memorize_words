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

class WordBookSelectionSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext,
            WordBookSelectionSyncWorkerEntryPoint::class.java
        )
        if (!entryPoint.authStateProvider().isAuthenticated()) {
            return Result.success()
        }

        val bookId = inputData.getLong(SyncWorkConstants.KEY_BOOK_ID, -1L)
        if (bookId <= 0L) {
            return Result.failure()
        }

        return try {
            entryPoint.remoteUserSyncDataSource()
                .setCurrentWordBookSelection(bookId)
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
interface WordBookSelectionSyncWorkerEntryPoint {
    fun remoteUserSyncDataSource(): RemoteUserSyncDataSource
    fun authStateProvider(): AuthStateProvider
}
