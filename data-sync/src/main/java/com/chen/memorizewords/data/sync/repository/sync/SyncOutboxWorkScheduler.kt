package com.chen.memorizewords.data.sync.repository.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
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
            ExistingWorkPolicy.KEEP,
            buildSyncOutboxDrainRequest(tag = SyncWorkConstants.TAG_SYNC_OUTBOX_IMMEDIATE_DRAIN)
        )
    }

    override fun scheduleRetryAt(nextAttemptAtMs: Long) {
        val workManager = runCatching { WorkManager.getInstance(appContext) }.getOrNull() ?: return
        val delayMs = (nextAttemptAtMs - System.currentTimeMillis()).coerceAtLeast(0L)
        val request = buildSyncOutboxDrainRequest(
            tag = SyncWorkConstants.TAG_SYNC_OUTBOX_RETRY,
            initialDelayMs = delayMs
        )
        workManager.enqueueUniqueWork(
            "${SyncWorkConstants.WORK_SYNC_OUTBOX_RETRY}_${nextAttemptAtMs / RETRY_BUCKET_MS}",
            ExistingWorkPolicy.KEEP,
            request
        )
    }

    override fun scheduleRecoveryDrain() {
        scheduleImmediateDrain()
    }

    override fun ensurePeriodicDrain() {
        val workManager = runCatching { WorkManager.getInstance(appContext) }.getOrNull() ?: return
        val request = PeriodicWorkRequestBuilder<SyncOutboxDrainWorker>(15, TimeUnit.MINUTES)
            .setConstraints(networkConstraints())
            .addTag(SyncWorkConstants.TAG_SYNC_OUTBOX_PERIODIC)
            .build()
        workManager.enqueueUniquePeriodicWork(
            SyncWorkConstants.WORK_SYNC_OUTBOX_PERIODIC,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    override fun cancelDrain() {
        val workManager = runCatching { WorkManager.getInstance(appContext) }.getOrNull() ?: return
        workManager.cancelUniqueWork(SyncWorkConstants.WORK_SYNC_OUTBOX_DRAIN)
        workManager.cancelUniqueWork(SyncWorkConstants.WORK_SYNC_OUTBOX_IMMEDIATE_DRAIN)
        workManager.cancelUniqueWork(SyncWorkConstants.WORK_SYNC_OUTBOX_RETRY)
        workManager.cancelUniqueWork(SyncWorkConstants.WORK_SYNC_OUTBOX_PERIODIC)
        workManager.cancelAllWorkByTag(SyncWorkConstants.TAG_SYNC_OUTBOX_DRAIN)
        workManager.cancelAllWorkByTag(SyncWorkConstants.TAG_SYNC_OUTBOX_IMMEDIATE_DRAIN)
        workManager.cancelAllWorkByTag(SyncWorkConstants.TAG_SYNC_OUTBOX_RETRY)
        workManager.cancelAllWorkByTag(SyncWorkConstants.TAG_SYNC_OUTBOX_PERIODIC)
    }
}

interface SyncOutboxDrainScheduler {
    fun scheduleDrain()
    fun scheduleImmediateDrain()
    fun scheduleRetryAt(nextAttemptAtMs: Long) = Unit
    fun scheduleRecoveryDrain() = scheduleImmediateDrain()
    fun ensurePeriodicDrain() = Unit
    fun cancelDrain() = Unit
}

internal fun buildSyncOutboxDrainRequest(tag: String, initialDelayMs: Long = 0L) =
    OneTimeWorkRequestBuilder<SyncOutboxDrainWorker>()
        .setConstraints(networkConstraints())
        .setInitialDelay(initialDelayMs, TimeUnit.MILLISECONDS)
        .addTag(tag)
        .build()

private fun networkConstraints(): Constraints = Constraints.Builder()
    .setRequiredNetworkType(NetworkType.CONNECTED)
    .build()

private const val RETRY_BUCKET_MS = 30_000L
