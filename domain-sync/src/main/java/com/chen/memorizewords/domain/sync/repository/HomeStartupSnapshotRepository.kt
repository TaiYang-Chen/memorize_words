package com.chen.memorizewords.domain.sync.repository

import com.chen.memorizewords.domain.sync.model.HomeStartupSnapshot
import kotlinx.coroutines.flow.Flow

interface HomeStartupSnapshotRepository {
    fun getSnapshot(): HomeStartupSnapshot?

    fun observeSnapshot(): Flow<HomeStartupSnapshot?>

    suspend fun saveSnapshot(snapshot: HomeStartupSnapshot)

    suspend fun clearSnapshot()
}
