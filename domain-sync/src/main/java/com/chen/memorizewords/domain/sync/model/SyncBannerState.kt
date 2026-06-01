package com.chen.memorizewords.domain.sync.model
sealed interface SyncBannerState {
    data object Hidden : SyncBannerState

    data class Offline(
        val pendingCount: Int
    ) : SyncBannerState

    data class Pending(
        val pendingCount: Int
    ) : SyncBannerState

    data class Blocked(
        val pendingCount: Int
    ) : SyncBannerState
}

data class PendingSyncSummary(
    val totalCount: Int,
    val hasNetwork: Boolean,
    val hasRetriableFailure: Boolean,
    val hasBlockedFailure: Boolean
)
