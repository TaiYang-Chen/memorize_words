package com.chen.memorizewords.data.sync.repository.sync

import com.chen.memorizewords.data.sync.bootstrap.DataBootstrapCoordinator
import com.chen.memorizewords.data.sync.local.room.model.sync.SyncOutboxDao
import com.chen.memorizewords.data.sync.local.room.model.sync.SyncOutboxEntity
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@Singleton
class SyncRepositoryImpl @Inject constructor(
    private val dataBootstrapCoordinator: DataBootstrapCoordinator,
    private val syncOutboxDao: SyncOutboxDao,
    private val networkMonitor: NetworkMonitor,
    private val syncOutboxWorkScheduler: SyncOutboxWorkScheduler,
    private val postLoginBootstrapStateStore: PostLoginBootstrapStateStore,
    private val learningPrerequisitesRestorer: LearningPrerequisitesRestorer
) : SyncRepository {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val pendingCountFlow = syncOutboxDao.observePendingCount()
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, 0)

    private val retryableCountFlow = syncOutboxDao.observeRetryableCount()
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, 0)

    private val blockedCountFlow = syncOutboxDao.observeBlockedCount()
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, 0)

    private val pendingSyncRecordsFlow = syncOutboxDao.observeAllPending()
        .map(::mapSyncPendingRecords)
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    private val syncBannerStateFlow = combine(
        pendingCountFlow,
        retryableCountFlow,
        blockedCountFlow,
        networkMonitor.isOnline
    ) { pendingCount, retryableCount, blockedCount, hasNetwork ->
        resolveSyncBannerState(
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

    override fun startPostLoginBootstrap() {
        if (postLoginBootstrapStateStore.getState() != PostLoginBootstrapState.Running) {
            postLoginBootstrapStateStore.setState(PostLoginBootstrapState.Running)
        }
        dataBootstrapCoordinator.schedulePostLoginBootstrapWork()
        triggerDrain()
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

    override fun observePostLoginBootstrapState(): Flow<PostLoginBootstrapState> =
        postLoginBootstrapStateFlow

    override fun observePendingSyncCount(): Flow<Int> = pendingCountFlow

    override fun observePendingSyncRecords(): Flow<List<SyncPendingRecord>> = pendingSyncRecordsFlow

    override fun observeSyncBannerState(): Flow<SyncBannerState> = syncBannerStateFlow

    override fun triggerDrain() {
        syncOutboxWorkScheduler.scheduleDrain()
    }
}

internal fun mapSyncPendingRecords(
    entities: List<SyncOutboxEntity>
): List<SyncPendingRecord> {
    return entities
        .sortedWith(
            compareBy<SyncOutboxEntity> { syncOutboxStatePriority(it.state.name) }
                .thenByDescending { it.updatedAt }
                .thenByDescending { it.id }
        )
        .map { entity ->
            SyncPendingRecord(
                id = entity.id,
                bizType = entity.bizType,
                bizKey = entity.bizKey,
                operation = entity.operation.name,
                payload = entity.payload,
                state = entity.state.name,
                retryCount = entity.retryCount,
                lastError = entity.lastError,
                failureKind = entity.failureKind?.name,
                lastAttemptAt = entity.lastAttemptAt,
                nextRetryAt = entity.nextRetryAt,
                updatedAt = entity.updatedAt
            )
        }
}

private fun syncOutboxStatePriority(state: String): Int {
    return when (state) {
        SyncOutboxState.BLOCKED.name -> 0
        SyncOutboxState.RETRY_WAITING.name -> 1
        SyncOutboxState.IN_FLIGHT.name -> 2
        SyncOutboxState.QUEUED.name -> 3
        else -> Int.MAX_VALUE
    }
}

internal fun resolveSyncBannerState(
    pendingCount: Int,
    retryableCount: Int,
    blockedCount: Int,
    hasNetwork: Boolean
): SyncBannerState {
    return when {
        pendingCount <= 0 -> SyncBannerState.Hidden
        !hasNetwork -> SyncBannerState.Offline(pendingCount)
        retryableCount > 0 -> SyncBannerState.Pending(pendingCount)
        blockedCount > 0 -> SyncBannerState.Blocked(pendingCount)
        else -> SyncBannerState.Pending(pendingCount)
    }
}
