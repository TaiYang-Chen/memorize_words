package com.chen.memorizewords.data.local.room.model.sync

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "sync_outbox",
    indices = [
        Index(value = ["biz_key"], unique = true),
        Index(value = ["state"]),
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
    val operation: String,

    @ColumnInfo(name = "payload")
    val payload: String,

    @ColumnInfo(name = "state")
    val state: String,

    @ColumnInfo(name = "retry_count")
    val retryCount: Int,

    @ColumnInfo(name = "last_error")
    val lastError: String?,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long
)
