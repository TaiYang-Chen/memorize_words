package com.chen.memorizewords.data.sync.repository.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncOutboxWorkScheduler @Inject constructor(
    @ApplicationContext context: Context
) : SyncOutboxDrainScheduler {
    private val appContext = context.applicationContext

    override fun scheduleDrain() {
        val workManager = runCatching { WorkManager.getInstance(appContext) }.getOrNull() ?: return

        workManager.enqueueUniqueWork(
            SyncWorkConstants.WORK_SYNC_OUTBOX_DRAIN,
            ExistingWorkPolicy.KEEP,
            buildDrainRequest(tag = SyncWorkConstants.TAG_SYNC_OUTBOX_DRAIN)
        )
    }

    override fun scheduleImmediateDrain() {
        val workManager = runCatching { WorkManager.getInstance(appContext) }.getOrNull() ?: return

        workManager.enqueueUniqueWork(
            SyncWorkConstants.immediateDrainWorkName(UUID.randomUUID().toString()),
            ExistingWorkPolicy.KEEP,
            buildDrainRequest(tag = SyncWorkConstants.TAG_SYNC_OUTBOX_IMMEDIATE_DRAIN)
        )
    }

    private fun buildDrainRequest(tag: String) =
        OneTimeWorkRequestBuilder<SyncOutboxDrainWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .addTag(tag)
            .build()
}

interface SyncOutboxDrainScheduler {
    fun scheduleDrain()
    fun scheduleImmediateDrain()
}
