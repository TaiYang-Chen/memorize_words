package com.chen.memorizewords.data.sync.local.room.model.sync

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "failed_sync_event",
    indices = [
        Index(value = ["state", "next_attempt_at_ms"]),
        Index(value = ["ordering_key", "sequence"]),
        Index(value = ["state", "lease_expires_at_ms"]),
        Index(value = ["event_type", "updated_at_ms"]),
        Index(value = ["dedupe_key"]),
        Index(value = ["lease_token"])
    ]
)
data class FailedSyncEventEntity(
    @PrimaryKey
    @ColumnInfo(name = "event_id")
    val eventId: String,
    @ColumnInfo(name = "event_type")
    val eventType: String,
    @ColumnInfo(name = "schema_version")
    val schemaVersion: Int,
    @ColumnInfo(name = "delivery_mode")
    val deliveryMode: FailedSyncDeliveryMode,
    @ColumnInfo(name = "dedupe_key")
    val dedupeKey: String?,
    @ColumnInfo(name = "ordering_key")
    val orderingKey: String,
    @ColumnInfo(name = "sequence")
    val sequence: Long?,
    @ColumnInfo(name = "params_json")
    val paramsJson: String,
    @ColumnInfo(name = "state")
    val state: FailedSyncState,
    @ColumnInfo(name = "attempt_count")
    val attemptCount: Int,
    @ColumnInfo(name = "last_error")
    val lastError: String?,
    @ColumnInfo(name = "next_attempt_at_ms")
    val nextAttemptAtMs: Long,
    @ColumnInfo(name = "lease_token")
    val leaseToken: String?,
    @ColumnInfo(name = "lease_expires_at_ms")
    val leaseExpiresAtMs: Long,
    @ColumnInfo(name = "occurred_at_ms")
    val occurredAtMs: Long,
    @ColumnInfo(name = "created_at_ms")
    val createdAtMs: Long,
    @ColumnInfo(name = "updated_at_ms")
    val updatedAtMs: Long
)

enum class FailedSyncDeliveryMode {
    APPEND,
    LATEST
}

enum class FailedSyncState {
    PENDING,
    IN_FLIGHT,
    RETRY_WAITING,
    BLOCKED
}
