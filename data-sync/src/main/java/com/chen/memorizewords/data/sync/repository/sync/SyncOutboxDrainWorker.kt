package com.chen.memorizewords.data.sync.repository.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class SyncOutboxDrainWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    // Kept only so persisted legacy WorkManager rows can finish safely after upgrade.
    // The old outbox must never be read or replayed again.
    override suspend fun doWork(): Result = Result.success()
}
