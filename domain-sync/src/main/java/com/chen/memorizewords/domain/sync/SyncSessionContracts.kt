package com.chen.memorizewords.domain.sync

enum class SyncDrainOutcome {
    EMPTY,
    DRAINED,
    RETRY_NEEDED
}

interface SyncLogoutFlusher {
    suspend fun getPendingCount(): Int
    suspend fun drainOnce(): SyncDrainOutcome
}

interface PostLoginBootstrapResetter {
    fun resetPostLoginBootstrap()
}
