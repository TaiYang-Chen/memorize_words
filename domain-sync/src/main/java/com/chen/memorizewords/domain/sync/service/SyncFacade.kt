package com.chen.memorizewords.domain.sync.service
import com.chen.memorizewords.domain.sync.model.LearningPrerequisitesSnapshot
import com.chen.memorizewords.domain.sync.model.PostLoginBootstrapState
import com.chen.memorizewords.domain.sync.model.SyncBannerState
import com.chen.memorizewords.domain.sync.model.SyncPendingRecord
import com.chen.memorizewords.domain.sync.repository.SyncRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class SyncFacade @Inject constructor(
    private val syncRepository: SyncRepository
) {
    fun observePostLoginBootstrapState(): Flow<PostLoginBootstrapState> =
        syncRepository.observePostLoginBootstrapState()

    fun getCurrentPostLoginBootstrapState(): PostLoginBootstrapState =
        syncRepository.getCurrentPostLoginBootstrapState()

    fun startPostLoginBootstrap() {
        syncRepository.startPostLoginBootstrap()
    }

    suspend fun syncAfterLogin(): Result<Unit> =
        syncRepository.syncAfterLogin()

    suspend fun restoreLearningPrerequisites(): Result<LearningPrerequisitesSnapshot> =
        syncRepository.restoreLearningPrerequisites()

    fun observePendingSyncRecords(): Flow<List<SyncPendingRecord>> =
        syncRepository.observePendingSyncRecords()

    fun observeSyncBannerState(): Flow<SyncBannerState> = syncRepository.observeSyncBannerState()

    fun triggerDrain() {
        syncRepository.triggerDrain()
    }
}
