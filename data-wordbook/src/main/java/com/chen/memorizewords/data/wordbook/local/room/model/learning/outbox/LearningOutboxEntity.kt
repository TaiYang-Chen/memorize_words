package com.chen.memorizewords.data.wordbook.local.room.model.learning.outbox

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "learning_outbox",
    indices = [
        Index(value = ["status", "next_retry_at_ms", "updated_at_ms"]),
        Index(value = ["lease_until_at_ms"]),
        Index(value = ["book_id", "word_id"])
    ]
)
data class LearningOutboxEntity(
    @PrimaryKey
    @ColumnInfo(name = "client_event_id")
    val clientEventId: String,

    @ColumnInfo(name = "book_id")
    val bookId: Long,

    @ColumnInfo(name = "word_id")
    val wordId: Long,

    @ColumnInfo(name = "payload")
    val payload: String,

    @ColumnInfo(name = "status")
    val status: String = STATUS_PENDING,

    @ColumnInfo(name = "attempt_count")
    val attemptCount: Int = 0,

    @ColumnInfo(name = "last_error")
    val lastError: String? = null,

    @ColumnInfo(name = "last_attempt_at_ms")
    val lastAttemptAt: Long? = null,

    @ColumnInfo(name = "next_retry_at_ms")
    val nextRetryAt: Long = 0L,

    @ColumnInfo(name = "lease_token")
    val leaseToken: String? = null,

    @ColumnInfo(name = "lease_until_at_ms")
    val leaseUntilAt: Long? = null,

    @ColumnInfo(name = "created_at_ms")
    val createdAtMs: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "updated_at_ms")
    val updatedAtMs: Long = System.currentTimeMillis()
) {
    companion object {
        const val STATUS_PENDING = "PENDING"
        const val STATUS_SYNCING = "SYNCING"
        const val STATUS_BLOCKED = "BLOCKED"
    }
}
