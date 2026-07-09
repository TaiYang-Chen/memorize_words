package com.chen.memorizewords.data.study.local.room.model.study.outbox

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "study_pending_outbox",
    indices = [
        Index(value = ["biz_key"], unique = true),
        Index(value = ["updated_at_ms", "id"])
    ]
)
data class StudyPendingOutboxEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    @ColumnInfo(name = "topic")
    val topic: String,
    @ColumnInfo(name = "biz_key")
    val bizKey: String,
    @ColumnInfo(name = "operation")
    val operation: String,
    @ColumnInfo(name = "payload")
    val payload: String,
    @ColumnInfo(name = "updated_at_ms")
    val updatedAtMs: Long
)
