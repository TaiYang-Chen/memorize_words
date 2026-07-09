package com.chen.memorizewords.data.wordbook.local.room.model.learning.event

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "learning_event",
    indices = [
        Index(value = ["book_id", "word_id"]),
        Index(value = ["book_id", "business_date"]),
        Index(value = ["created_at_ms"])
    ]
)
data class LearningEventEntity(
    @PrimaryKey
    @ColumnInfo(name = "client_event_id")
    val clientEventId: String,

    @ColumnInfo(name = "device_id")
    val deviceId: String? = null,

    @ColumnInfo(name = "client_sequence")
    val clientSequence: Long,

    @ColumnInfo(name = "book_id")
    val bookId: Long,

    @ColumnInfo(name = "word_id")
    val wordId: Long,

    @ColumnInfo(name = "action")
    val action: String,

    @ColumnInfo(name = "quality")
    val quality: Int?,

    @ColumnInfo(name = "correct")
    val correct: Boolean?,

    @ColumnInfo(name = "business_date")
    val businessDate: String,

    @ColumnInfo(name = "occurred_at_ms")
    val occurredAt: Long,

    @ColumnInfo(name = "base_state_revision")
    val baseStateRevision: Long,

    @ColumnInfo(name = "server_state_revision")
    val serverStateRevision: Long? = null,

    @ColumnInfo(name = "before_state_json")
    val beforeStateJson: String? = null,

    @ColumnInfo(name = "after_state_json")
    val afterStateJson: String? = null,

    @ColumnInfo(name = "payload_json")
    val payloadJson: String? = null,

    @ColumnInfo(name = "schema_version")
    val schemaVersion: Int = 1,

    @ColumnInfo(name = "created_at_ms")
    val createdAtMs: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "synced_at_ms")
    val syncedAt: Long? = null
)
