package com.chen.memorizewords.data.wordbook.local.room.model.wordbook.syncstate

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import com.chen.memorizewords.data.wordbook.local.room.model.wordbook.wordbook.WordBookEntity
import com.chen.memorizewords.domain.wordbook.model.WordBookUpdateTrigger

@Entity(
    tableName = "word_book_sync_state",
    foreignKeys = [
        ForeignKey(
            entity = WordBookEntity::class,
            parentColumns = ["id"],
            childColumns = ["book_id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class WordBookSyncStateEntity(
    @PrimaryKey
    @ColumnInfo(name = "book_id")
    val bookId: Long,
    @ColumnInfo(name = "local_version")
    val localVersion: Long = 0L,
    @ColumnInfo(name = "remote_version")
    val remoteVersion: Long = 0L,
    @ColumnInfo(name = "pending_target_version")
    val pendingTargetVersion: Long? = null,
    @ColumnInfo(name = "ignored_version")
    val ignoredVersion: Long? = null,
    @ColumnInfo(name = "last_prompted_version")
    val lastPromptedVersion: Long? = null,
    @ColumnInfo(name = "deferred_until")
    val deferredUntil: Long? = null,
    @ColumnInfo(name = "last_prompt_at")
    val lastPromptAt: Long? = null,
    @ColumnInfo(name = "last_prompt_source")
    val lastPromptSource: WordBookUpdateTrigger? = null,
    @ColumnInfo(name = "last_checked_at")
    val lastCheckedAt: Long? = null,
    @ColumnInfo(name = "last_completed_at")
    val lastCompletedAt: Long? = null,
    @ColumnInfo(name = "last_failure_reason")
    val lastFailureReason: String? = null
) {
    init {
        require(bookId > 0L) { "bookId must be positive" }
        require(localVersion >= 0L) { "localVersion must be non-negative" }
        require(remoteVersion >= 0L) { "remoteVersion must be non-negative" }
        require(pendingTargetVersion == null || pendingTargetVersion >= 0L) {
            "pendingTargetVersion must be non-negative"
        }
        require(ignoredVersion == null || ignoredVersion >= 0L) {
            "ignoredVersion must be non-negative"
        }
        require(lastPromptedVersion == null || lastPromptedVersion >= 0L) {
            "lastPromptedVersion must be non-negative"
        }
        require(deferredUntil == null || deferredUntil >= 0L) {
            "deferredUntil must be non-negative"
        }
        require(lastPromptAt == null || lastPromptAt >= 0L) {
            "lastPromptAt must be non-negative"
        }
        require(lastCheckedAt == null || lastCheckedAt >= 0L) {
            "lastCheckedAt must be non-negative"
        }
        require(lastCompletedAt == null || lastCompletedAt >= 0L) {
            "lastCompletedAt must be non-negative"
        }
    }
}
