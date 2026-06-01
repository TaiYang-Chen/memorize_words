package com.chen.memorizewords.data.sync.local.room.model.sync

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.chen.memorizewords.data.sync.repository.sync.SyncOutboxFailureKind
import com.chen.memorizewords.data.sync.repository.sync.SyncOutboxOperation
import com.chen.memorizewords.data.sync.repository.sync.SyncOutboxState

@Entity(
    tableName = "sync_outbox",
    indices = [
        Index(value = ["biz_key"], unique = true),
        Index(value = ["biz_type", "updated_at"]),
        Index(value = ["state", "next_retry_at", "updated_at"]),
        Index(value = ["state", "lease_expires_at", "updated_at"]),
        Index(value = ["lease_token"]),
        Index(value = ["updated_at"])
    ]
)
data class SyncOutboxEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "biz_type")
    val bizType: String,
    @ColumnInfo(name = "biz_key")
    val bizKey: String,
    @ColumnInfo(name = "operation")
    val operation: SyncOutboxOperation,
    @ColumnInfo(name = "payload")
    val payload: String,
    @ColumnInfo(name = "state")
    val state: SyncOutboxState,
    @ColumnInfo(name = "retry_count")
    val retryCount: Int,
    @ColumnInfo(name = "last_error")
    val lastError: String?,
    @ColumnInfo(name = "failure_kind")
    val failureKind: SyncOutboxFailureKind?,
    @ColumnInfo(name = "last_attempt_at")
    val lastAttemptAt: Long,
    @ColumnInfo(name = "next_retry_at")
    val nextRetryAt: Long,
    @ColumnInfo(name = "lease_token")
    val leaseToken: String?,
    @ColumnInfo(name = "lease_expires_at")
    val leaseExpiresAt: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long
)
