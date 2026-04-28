package com.chen.memorizewords.domain.repository.sync

import com.chen.memorizewords.domain.model.sync.PostLoginBootstrapState
import com.chen.memorizewords.domain.model.sync.SyncBannerState
import com.chen.memorizewords.domain.model.sync.SyncPendingRecord
import kotlinx.coroutines.flow.Flow

interface SyncRepository {
    fun startPostLoginBootstrap()
    fun getCurrentPostLoginBootstrapState(): PostLoginBootstrapState
    fun scheduleBootstrapSync()
    fun observePostLoginBootstrapState(): Flow<PostLoginBootstrapState>
    fun observePendingSyncCount(): Flow<Int>
    fun observePendingSyncRecords(): Flow<List<SyncPendingRecord>>
    fun observeSyncBannerState(): Flow<SyncBannerState>
    fun triggerDrain()
}
