package com.chen.memorizewords.domain.model.sync

sealed interface SyncBannerState {
    data object Hidden : SyncBannerState

    data class Offline(
        val pendingCount: Int
    ) : SyncBannerState

    data class Failed(
        val pendingCount: Int
    ) : SyncBannerState
}

data class PendingSyncSummary(
    val totalCount: Int,
    val hasNetwork: Boolean,
    val hasRetriableFailure: Boolean
)
