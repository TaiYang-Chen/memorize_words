package com.chen.memorizewords.data.repository.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.chen.memorizewords.data.remote.datasync.RemoteUserSyncDataSource
import com.chen.memorizewords.domain.auth.AuthStateProvider
import com.chen.memorizewords.domain.model.study.favorites.WordFavorites
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.io.IOException

class FavoriteSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext,
            FavoriteSyncWorkerEntryPoint::class.java
        )
        if (!entryPoint.authStateProvider().isAuthenticated()) {
            return Result.success()
        }

        val action = inputData.getString(SyncWorkConstants.KEY_FAVORITE_ACTION)
        val wordId = inputData.getLong(SyncWorkConstants.KEY_WORD_ID, -1L)
        if (wordId <= 0L || action.isNullOrBlank()) {
            return Result.failure()
        }

        return try {
            when (action) {
                SyncWorkConstants.ACTION_ADD_FAVORITE -> {
                    val word = inputData.getString(SyncWorkConstants.KEY_WORD)
                    val definitions = inputData.getString(SyncWorkConstants.KEY_DEFINITIONS)
                    val phonetic = inputData.getString(SyncWorkConstants.KEY_PHONETIC)
                    val addedDate = inputData.getString(SyncWorkConstants.KEY_ADDED_DATE)

                    if (word.isNullOrBlank() || definitions == null || addedDate.isNullOrBlank()) {
                        return Result.failure()
                    }

                    entryPoint.remoteUserSyncDataSource()
                        .addFavorite(
                            WordFavorites(
                                wordId = wordId,
                                word = word,
                                definitions = definitions,
                                phonetic = phonetic,
                                addedDate = addedDate
                            )
                        )
                        .getOrThrow()
                }

                SyncWorkConstants.ACTION_REMOVE_FAVORITE -> {
                    entryPoint.remoteUserSyncDataSource()
                        .removeFavorite(wordId)
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
interface FavoriteSyncWorkerEntryPoint {
    fun remoteUserSyncDataSource(): RemoteUserSyncDataSource
    fun authStateProvider(): AuthStateProvider
}
