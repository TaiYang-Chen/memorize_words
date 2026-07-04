package com.chen.memorizewords.data.sync.repository.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
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
            buildSyncOutboxDrainRequest(tag = SyncWorkConstants.TAG_SYNC_OUTBOX_DRAIN)
        )
    }

    override fun scheduleImmediateDrain() {
        val workManager = runCatching { WorkManager.getInstance(appContext) }.getOrNull() ?: return

        workManager.enqueueUniqueWork(
            SyncWorkConstants.WORK_SYNC_OUTBOX_IMMEDIATE_DRAIN,
            ExistingWorkPolicy.REPLACE,
            buildSyncOutboxDrainRequest(tag = SyncWorkConstants.TAG_SYNC_OUTBOX_IMMEDIATE_DRAIN)
        )
    }

    override fun cancelDrain() {
        val workManager = runCatching { WorkManager.getInstance(appContext) }.getOrNull() ?: return
        workManager.cancelUniqueWork(SyncWorkConstants.WORK_SYNC_OUTBOX_DRAIN)
        workManager.cancelUniqueWork(SyncWorkConstants.WORK_SYNC_OUTBOX_IMMEDIATE_DRAIN)
        workManager.cancelAllWorkByTag(SyncWorkConstants.TAG_SYNC_OUTBOX_DRAIN)
        workManager.cancelAllWorkByTag(SyncWorkConstants.TAG_SYNC_OUTBOX_IMMEDIATE_DRAIN)
    }
}

interface SyncOutboxDrainScheduler {
    fun scheduleDrain()
    fun scheduleImmediateDrain()
    fun cancelDrain() = Unit
}

internal fun buildSyncOutboxDrainRequest(tag: String) =
    OneTimeWorkRequestBuilder<SyncOutboxDrainWorker>()
        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
        .addTag(tag)
        .build()
