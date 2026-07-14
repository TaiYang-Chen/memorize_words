package com.chen.memorizewords.domain.sync

import kotlinx.coroutines.flow.Flow

interface PendingCheckInSyncQuery {
    fun observePendingMakeupCheckInCount(): Flow<Int>

    suspend fun countPendingMakeupCheckIns(): Int
}
