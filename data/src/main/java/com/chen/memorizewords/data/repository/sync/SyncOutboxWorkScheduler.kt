package com.chen.memorizewords.data.repository.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncOutboxWorkScheduler @Inject constructor(
    @ApplicationContext context: Context
) {
    private val appContext = context.applicationContext

    fun scheduleDrain() {
        val workManager = runCatching { WorkManager.getInstance(appContext) }.getOrNull() ?: return
        val request = OneTimeWorkRequestBuilder<SyncOutboxDrainWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .addTag(SyncWorkConstants.TAG_SYNC_OUTBOX_DRAIN)
            .build()

        workManager.enqueueUniqueWork(
            SyncWorkConstants.WORK_SYNC_OUTBOX_DRAIN,
            ExistingWorkPolicy.KEEP,
            request
        )
    }
}
