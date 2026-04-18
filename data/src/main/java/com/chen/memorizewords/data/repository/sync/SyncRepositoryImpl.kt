package com.chen.memorizewords.data.repository.sync

import com.chen.memorizewords.data.bootstrap.DataBootstrapCoordinator
import com.chen.memorizewords.data.local.room.model.sync.SyncOutboxDao
import com.chen.memorizewords.domain.model.sync.PostLoginBootstrapState
import com.chen.memorizewords.domain.model.sync.SyncBannerState
import com.chen.memorizewords.domain.repository.sync.SyncRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@Singleton
class SyncRepositoryImpl @Inject constructor(
    private val dataBootstrapCoordinator: DataBootstrapCoordinator,
    private val syncOutboxDao: SyncOutboxDao,
    private val networkMonitor: NetworkMonitor,
    private val syncOutboxWorkScheduler: SyncOutboxWorkScheduler,
    private val postLoginBootstrapStateStore: PostLoginBootstrapStateStore
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

    init {
        scope.launch {
            combine(retryableCountFlow, networkMonitor.isOnline) { retryableCount, hasNetwork ->
                retryableCount to hasNetwork
            }.collect { (retryableCount, hasNetwork) ->
                if (hasNetwork && retryableCount > 0) {
                    syncOutboxWorkScheduler.scheduleDrain()
                }
            }
        }
    }

    override fun startPostLoginBootstrap() {
        if (postLoginBootstrapStateStore.getState() != PostLoginBootstrapState.Running) {
            postLoginBootstrapStateStore.setState(PostLoginBootstrapState.Running)
        }
        dataBootstrapCoordinator.schedulePostLoginBootstrapWork()
        triggerDrain()
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

    override fun observeSyncBannerState(): Flow<SyncBannerState> = syncBannerStateFlow

    override fun triggerDrain() {
        syncOutboxWorkScheduler.scheduleDrain()
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
