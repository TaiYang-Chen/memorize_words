package com.chen.memorizewords.data.sync.repository.sync

import com.chen.memorizewords.data.sync.bootstrap.DataBootstrapCoordinator
import com.chen.memorizewords.data.sync.bootstrap.PostLoginBootstrapErrorLogger
import com.chen.memorizewords.data.sync.local.room.model.sync.FailedSyncEventDao
import com.chen.memorizewords.data.sync.local.room.model.sync.FailedSyncEventEntity
import com.chen.memorizewords.data.sync.local.room.model.sync.FailedSyncState
import com.chen.memorizewords.domain.account.auth.AuthStateProvider
import com.chen.memorizewords.domain.sync.model.LearningPrerequisitesSnapshot
import com.chen.memorizewords.domain.sync.model.PostLoginBootstrapState
import com.chen.memorizewords.domain.sync.model.SyncBannerState
import com.chen.memorizewords.domain.sync.model.SyncPendingRecord
import com.chen.memorizewords.domain.sync.repository.SyncRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@Singleton
class SyncRepositoryImpl @Inject constructor(
    private val dataBootstrapCoordinator: DataBootstrapCoordinator,
    private val failedSyncEventDao: FailedSyncEventDao,
    private val networkMonitor: NetworkMonitor,
    private val failedSyncScheduler: FailedSyncScheduler,
    private val failureQueueResetter: FailureQueueResetter,
    private val failedEventPendingSignal: FailedEventPendingSignal,
    private val networkRecoveryNotifier: NetworkRecoveryNotifier,
    private val authStateProvider: AuthStateProvider,
    private val postLoginBootstrapStateStore: PostLoginBootstrapStateStore,
    private val learningPrerequisitesRestorer: LearningPrerequisitesRestorer,
    private val errorLogger: PostLoginBootstrapErrorLogger
) : SyncRepository {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val pendingCountFlow = failedSyncEventDao.observePendingCount()
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, 0)

    private val retryableCountFlow = failedSyncEventDao.observeRetryableCount()
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, 0)

    private val blockedCountFlow = failedSyncEventDao.observeBlockedCount()
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, 0)

    private val pendingSyncRecordsFlow = failedSyncEventDao.observeAllPending()
        .map(::mapFailedSyncPendingRecords)
        .distinctUntilChanged()

    private val syncBannerStateFlow = combine(
        pendingCountFlow,
        retryableCountFlow,
        blockedCountFlow,
        networkMonitor.isOnline,
        authStateProvider.observeAuthenticated()
    ) { pendingCount, retryableCount, blockedCount, hasNetwork, isAuthenticated ->
        resolveSyncBannerState(
            isAuthenticated = isAuthenticated,
            pendingCount = pendingCount,
            retryableCount = retryableCount,
            blockedCount = blockedCount,
            hasNetwork = hasNetwork
        )
    }.distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, SyncBannerState.Hidden)

    private val postLoginBootstrapStateFlow = postLoginBootstrapStateStore.observeState()
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, postLoginBootstrapStateStore.getState())

    init {
        failedSyncScheduler.ensurePeriodic()
        scope.launch {
            failedEventPendingSignal.initialize(failedSyncEventDao.retryableCount() > 0)
        }
        scope.launch {
            networkMonitor.networkCandidateAvailable
                .filter { it }
                .collect { networkRecoveryNotifier.onNetworkAvailable() }
        }
    }

    override fun startPostLoginBootstrap() {
        if (postLoginBootstrapStateStore.getState() != PostLoginBootstrapState.Running) {
            postLoginBootstrapStateStore.setState(PostLoginBootstrapState.Running)
        }
        dataBootstrapCoordinator.schedulePostLoginBootstrapWork()
        triggerDrain()
    }

    override suspend fun syncAfterLogin(): Result<Unit> {
        postLoginBootstrapStateStore.setState(PostLoginBootstrapState.Running)
        return runCatching {
            dataBootstrapCoordinator.syncAfterLoginInOrder()
            postLoginBootstrapStateStore.setState(PostLoginBootstrapState.Succeeded)
        }.onFailure { throwable ->
            errorLogger.logFailure(
                source = "SyncRepositoryImpl.syncAfterLogin",
                throwable = throwable
            )
            postLoginBootstrapStateStore.setState(PostLoginBootstrapState.Failed)
        }
    }

    override suspend fun restoreLearningPrerequisites(): Result<LearningPrerequisitesSnapshot> {
        return learningPrerequisitesRestorer.restore()
    }

    override fun getCurrentPostLoginBootstrapState(): PostLoginBootstrapState {
        return postLoginBootstrapStateStore.getState()
    }

    override fun scheduleBootstrapSync() {
        dataBootstrapCoordinator.scheduleBootstrapWork()
    }

    override suspend fun discardLocalPendingSyncOnLogin() {
        failureQueueResetter.reset()
    }

    override fun observePostLoginBootstrapState(): Flow<PostLoginBootstrapState> =
        postLoginBootstrapStateFlow

    override fun observePendingSyncCount(): Flow<Int> = pendingCountFlow

    override fun observePendingSyncRecords(): Flow<List<SyncPendingRecord>> = pendingSyncRecordsFlow

    override fun observeSyncBannerState(): Flow<SyncBannerState> = syncBannerStateFlow

    override fun triggerDrain() {
        failedSyncScheduler.ensurePeriodic()
        failedSyncScheduler.scheduleDrain()
    }

}

internal fun mapFailedSyncPendingRecords(
    entities: List<FailedSyncEventEntity>
): List<SyncPendingRecord> {
    return entities
        .sortedWith(
            compareBy<FailedSyncEventEntity> { failedSyncStatePriority(it.state) }
                .thenByDescending { it.updatedAtMs }
                .thenByDescending { it.eventId }
        )
        .map { entity ->
            SyncPendingRecord(
                id = "failed:${entity.eventId}",
                sourceId = SOURCE_FAILED,
                bizType = entity.eventType,
                bizKey = entity.dedupeKey ?: entity.orderingKey,
                operation = entity.deliveryMode.name,
                payload = entity.paramsJson,
                state = entity.state.name,
                retryCount = entity.attemptCount,
                lastError = entity.lastError,
                failureKind = when (entity.state) {
                    FailedSyncState.BLOCKED -> SyncOutboxFailureKind.UNKNOWN.name
                    else -> SyncOutboxFailureKind.NETWORK.name
                },
                lastAttemptAt = if (entity.attemptCount > 0) entity.updatedAtMs else 0L,
                nextRetryAt = if (entity.state == FailedSyncState.RETRY_WAITING) {
                    entity.nextAttemptAtMs
                } else {
                    0L
                },
                updatedAtMs = entity.updatedAtMs
            )
        }
}

private const val SOURCE_FAILED = "failed"

private fun failedSyncStatePriority(state: FailedSyncState): Int {
    return when (state) {
        FailedSyncState.BLOCKED -> 0
        FailedSyncState.RETRY_WAITING -> 1
        FailedSyncState.IN_FLIGHT -> 2
        FailedSyncState.PENDING -> 3
    }
}

internal fun resolveSyncBannerState(
    isAuthenticated: Boolean,
    pendingCount: Int,
    retryableCount: Int,
    blockedCount: Int,
    hasNetwork: Boolean
): SyncBannerState {
    return when {
        !isAuthenticated -> SyncBannerState.Hidden
        pendingCount <= 0 -> SyncBannerState.Hidden
        !hasNetwork -> SyncBannerState.Offline(pendingCount)
        retryableCount > 0 -> SyncBannerState.Pending(pendingCount)
        blockedCount > 0 -> SyncBannerState.Blocked(pendingCount)
        else -> SyncBannerState.Pending(pendingCount)
    }
}
