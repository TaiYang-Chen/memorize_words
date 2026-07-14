package com.chen.memorizewords.data.sync.repository.sync

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FailedSyncWorkScheduler @Inject constructor(
    @ApplicationContext context: Context
) : FailedSyncScheduler {
    private val workManager = WorkManager.getInstance(context.applicationContext)
    private val legacyCleanupStarted = AtomicBoolean(false)

    override fun scheduleDrain() {
        workManager.enqueueUniqueWork(
            WORK_DRAIN,
            ExistingWorkPolicy.KEEP,
            request(TAG_DRAIN)
        )
    }

    override fun scheduleContinuation() {
        // A running unique drain cannot enqueue itself with KEEP. A tagged, non-unique
        // continuation is used only when the engine confirms that immediately claimable
        // work remains, and the engine mutex still guarantees a single active drain.
        workManager.enqueue(request(TAG_DRAIN))
    }

    override fun scheduleRecovery() {
        workManager.enqueueUniqueWork(
            WORK_RECOVERY,
            ExistingWorkPolicy.KEEP,
            request(
                tag = TAG_RECOVERY,
                input = Data.Builder().putBoolean(SyncRetryWorker.KEY_RECOVERY, true).build()
            )
        )
    }

    override fun scheduleRetryAt(nextAttemptAtMs: Long) {
        val delay = (nextAttemptAtMs - System.currentTimeMillis()).coerceAtLeast(0L)
        workManager.enqueueUniqueWork(
            "$WORK_RETRY${nextAttemptAtMs / RETRY_BUCKET_MS}",
            ExistingWorkPolicy.KEEP,
            request(TAG_RETRY, delay)
        )
    }

    override fun ensurePeriodic() {
        cleanupLegacyWorkOnce()
        workManager.enqueueUniquePeriodicWork(
            WORK_PERIODIC,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<SyncRetryWorker>(15, TimeUnit.MINUTES)
                .addTag(TAG_PERIODIC)
                .build()
        )
    }

    override fun cancelAll() {
        listOf(WORK_DRAIN, WORK_RECOVERY, WORK_PERIODIC).forEach(workManager::cancelUniqueWork)
        listOf(TAG_DRAIN, TAG_RECOVERY, TAG_RETRY, TAG_PERIODIC).forEach(workManager::cancelAllWorkByTag)
        cancelLegacyFailedQueueWork()
    }

    private fun cancelLegacySyncWork() {
        listOf(
            SyncWorkConstants.WORK_SYNC_OUTBOX_DRAIN,
            SyncWorkConstants.WORK_SYNC_OUTBOX_IMMEDIATE_DRAIN,
            SyncWorkConstants.WORK_SYNC_OUTBOX_RETRY,
            SyncWorkConstants.WORK_SYNC_OUTBOX_PERIODIC
        ).forEach(workManager::cancelUniqueWork)
        listOf(
            SyncWorkConstants.TAG_SYNC_OUTBOX_DRAIN,
            SyncWorkConstants.TAG_SYNC_OUTBOX_IMMEDIATE_DRAIN,
            SyncWorkConstants.TAG_SYNC_OUTBOX_RETRY,
            SyncWorkConstants.TAG_SYNC_OUTBOX_PERIODIC
        ).forEach(workManager::cancelAllWorkByTag)
    }

    private fun cleanupLegacyWorkOnce() {
        if (!legacyCleanupStarted.compareAndSet(false, true)) return
        cancelLegacySyncWork()
        cancelLegacyFailedQueueWork()
    }

    private fun cancelLegacyFailedQueueWork() {
        listOf(
            LEGACY_WORK_DRAIN,
            LEGACY_WORK_RECOVERY,
            LEGACY_WORK_PERIODIC
        ).forEach(workManager::cancelUniqueWork)
        listOf(
            LEGACY_TAG_DRAIN,
            LEGACY_TAG_RECOVERY,
            LEGACY_TAG_RETRY,
            LEGACY_TAG_PERIODIC
        ).forEach(workManager::cancelAllWorkByTag)
    }

    private fun request(tag: String, delayMs: Long = 0L, input: Data = Data.EMPTY) =
        OneTimeWorkRequestBuilder<SyncRetryWorker>()
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .setInputData(input)
            .addTag(tag)
            .build()

    private companion object {
        const val WORK_DRAIN = "failed_sync_drain_v2"
        const val WORK_RECOVERY = "failed_sync_recovery_v2"
        const val WORK_RETRY = "failed_sync_retry_v2_"
        const val WORK_PERIODIC = "failed_sync_periodic_v2"
        const val TAG_DRAIN = "failed_sync_drain_v2"
        const val TAG_RECOVERY = "failed_sync_recovery_v2"
        const val TAG_RETRY = "failed_sync_retry_v2"
        const val TAG_PERIODIC = "failed_sync_periodic_v2"
        const val LEGACY_WORK_DRAIN = "failed_sync_drain"
        const val LEGACY_WORK_RECOVERY = "failed_sync_recovery"
        const val LEGACY_WORK_PERIODIC = "failed_sync_periodic"
        const val LEGACY_TAG_DRAIN = "failed_sync_drain"
        const val LEGACY_TAG_RECOVERY = "failed_sync_recovery"
        const val LEGACY_TAG_RETRY = "failed_sync_retry"
        const val LEGACY_TAG_PERIODIC = "failed_sync_periodic"
        const val RETRY_BUCKET_MS = 30_000L
    }
}
