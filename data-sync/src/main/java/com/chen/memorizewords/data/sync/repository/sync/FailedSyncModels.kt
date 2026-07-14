package com.chen.memorizewords.data.sync.repository.sync

import com.chen.memorizewords.data.sync.local.room.model.sync.FailedSyncDeliveryMode
import com.chen.memorizewords.data.sync.local.room.model.sync.FailedSyncState

data class FailedSyncEvent(
    val eventId: String,
    val eventType: String,
    val schemaVersion: Int,
    val deliveryMode: FailedSyncDeliveryMode,
    val dedupeKey: String?,
    val orderingKey: String,
    val sequence: Long?,
    val paramsJson: String,
    val occurredAtMs: Long,
    val initialState: FailedSyncState = FailedSyncState.PENDING,
    val initialError: String? = null
)

data class LatestSyncIdentity(
    val dedupeKey: String
)
