package com.chen.memorizewords.domain.service.sync

import com.chen.memorizewords.domain.model.sync.PostLoginBootstrapState
import com.chen.memorizewords.domain.model.sync.SyncBannerState
import com.chen.memorizewords.domain.repository.sync.SyncRepository
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

    fun observeSyncBannerState(): Flow<SyncBannerState> = syncRepository.observeSyncBannerState()

    fun triggerDrain() {
        syncRepository.triggerDrain()
    }
}
