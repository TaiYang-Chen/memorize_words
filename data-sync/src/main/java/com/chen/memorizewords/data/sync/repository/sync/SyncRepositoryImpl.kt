package com.chen.memorizewords.data.sync.repository.sync

import com.chen.memorizewords.data.sync.bootstrap.DataBootstrapCoordinator
import com.chen.memorizewords.data.sync.bootstrap.PostLoginBootstrapErrorLogger
import com.chen.memorizewords.data.sync.local.room.model.sync.SyncOutboxDao
import com.chen.memorizewords.data.sync.local.room.model.sync.SyncOutboxEntity
import com.chen.memorizewords.data.wordbook.local.room.model.learning.outbox.LearningOutboxDao
import com.chen.memorizewords.data.wordbook.local.room.model.learning.outbox.LearningOutboxEntity
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@Singleton
class SyncRepositoryImpl @Inject constructor(
    private val dataBootstrapCoordinator: DataBootstrapCoordinator,
    private val syncOutboxDao: SyncOutboxDao,
    private val learningOutboxDao: LearningOutboxDao,
    private val networkMonitor: NetworkMonitor,
    private val syncOutboxWorkScheduler: SyncOutboxWorkScheduler,
    private val authStateProvider: AuthStateProvider,
    private val postLoginBootstrapStateStore: PostLoginBootstrapStateStore,
    private val learningPrerequisitesRestorer: LearningPrerequisitesRestorer,
    private val errorLogger: PostLoginBootstrapErrorLogger,
    private val staleBlockedWordBookDeleteOutboxCleaner: StaleBlockedWordBookDeleteOutboxCleaner
) : SyncRepository {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val learningPendingCountFlow = learningOutboxDao.observeCountByStatuses(
        listOf(
            LearningOutboxEntity.STATUS_PENDING,
            LearningOutboxEntity.STATUS_SYNCING,
            LearningOutboxEntity.STATUS_BLOCKED
        )
    )
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, 0)

    private val learningRetryableCountFlow = learningOutboxDao.observeCountByStatus(
        LearningOutboxEntity.STATUS_PENDING
    )
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, 0)

    private val learningBlockedCountFlow = learningOutboxDao.observeCountByStatus(
        LearningOutboxEntity.STATUS_BLOCKED
    )
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, 0)

    private val pendingCountFlow = combine(
        syncOutboxDao.observePendingCount(),
        learningPendingCountFlow
    ) { globalCount, learningCount -> globalCount + learningCount }
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, 0)

    private val retryableCountFlow = combine(
        syncOutboxDao.observeRetryableCount(),
        learningRetryableCountFlow
    ) { globalCount, learningCount -> globalCount + learningCount }
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, 0)

    private val blockedCountFlow = combine(
        syncOutboxDao.observeBlockedCount(),
        learningBlockedCountFlow
    ) { globalCount, learningCount -> globalCount + learningCount }
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
        scope.launch {
            staleBlockedWordBookDeleteOutboxCleaner.clean()
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
        syncOutboxWorkScheduler.cancelDrain()
        syncOutboxDao.deleteAll()
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
                .thenByDescending { it.updatedAtMs }
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
                updatedAtMs = entity.updatedAtMs
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
