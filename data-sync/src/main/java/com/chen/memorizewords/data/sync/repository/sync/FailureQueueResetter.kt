package com.chen.memorizewords.data.sync.repository.sync

import com.chen.memorizewords.core.common.coroutines.DirectSyncLauncher
import com.chen.memorizewords.data.sync.local.room.model.sync.FailedSyncEventDao
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FailureQueueResetter @Inject constructor(
    private val sessionGate: FailureQueueSessionGate,
    private val recoveryNotifier: NetworkRecoveryNotifier,
    private val latestSyncRequestCoordinator: LatestSyncRequestCoordinator,
    private val scheduler: FailedSyncScheduler,
    private val dao: FailedSyncEventDao,
    private val pendingSignal: FailedEventPendingSignal,
    private val directSyncLauncher: DirectSyncLauncher
) {
    suspend fun reset() {
        sessionGate.invalidateAndRun {
            directSyncLauncher.cancelAll()
            latestSyncRequestCoordinator.onSessionInvalidated()
            recoveryNotifier.onSessionInvalidated()
            scheduler.cancelAll()
            dao.deleteAll()
            pendingSignal.clear()
        }
    }
}
