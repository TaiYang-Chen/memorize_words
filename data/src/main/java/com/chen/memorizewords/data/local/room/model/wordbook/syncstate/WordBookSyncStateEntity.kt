package com.chen.memorizewords.data.local.room.model.wordbook.syncstate

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "word_book_sync_state")
data class WordBookSyncStateEntity(
    @PrimaryKey
    @ColumnInfo(name = "book_id")
    val bookId: Long,
    @ColumnInfo(name = "local_version")
    val localVersion: Long = 0L,
    @ColumnInfo(name = "remote_version")
    val remoteVersion: Long = 0L,
    @ColumnInfo(name = "pending_target_version")
    val pendingTargetVersion: Long = 0L,
    @ColumnInfo(name = "ignored_version")
    val ignoredVersion: Long = 0L,
    @ColumnInfo(name = "last_prompted_version")
    val lastPromptedVersion: Long = 0L,
    @ColumnInfo(name = "deferred_until")
    val deferredUntil: Long = 0L,
    @ColumnInfo(name = "last_prompt_at")
    val lastPromptAt: Long = 0L,
    @ColumnInfo(name = "last_prompt_source")
    val lastPromptSource: String? = null,
    @ColumnInfo(name = "last_checked_at")
    val lastCheckedAt: Long = 0L,
    @ColumnInfo(name = "last_completed_at")
    val lastCompletedAt: Long = 0L,
    @ColumnInfo(name = "last_failure_reason")
    val lastFailureReason: String? = null
)
