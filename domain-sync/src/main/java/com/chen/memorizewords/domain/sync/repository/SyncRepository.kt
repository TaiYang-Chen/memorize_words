package com.chen.memorizewords.domain.sync.repository
import com.chen.memorizewords.domain.sync.model.PostLoginBootstrapState
import com.chen.memorizewords.domain.sync.model.SyncBannerState
import com.chen.memorizewords.domain.sync.model.SyncPendingRecord
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
