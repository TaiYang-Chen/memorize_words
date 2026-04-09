package com.chen.memorizewords.data.bootstrap

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

class DataBootstrapWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext,
            DataBootstrapWorkerEntryPoint::class.java
        )
        return runCatching {
            entryPoint.dataBootstrapCoordinator().bootstrapMyBooksAndEnqueueDownloads()
            Result.success()
        }.getOrElse { Result.retry() }
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface DataBootstrapWorkerEntryPoint {
    fun dataBootstrapCoordinator(): DataBootstrapCoordinator
}
