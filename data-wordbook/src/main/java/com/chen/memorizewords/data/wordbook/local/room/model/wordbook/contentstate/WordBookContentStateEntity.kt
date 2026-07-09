package com.chen.memorizewords.data.wordbook.local.room.model.wordbook.contentstate

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "word_book_content_state")
data class WordBookContentStateEntity(
    @PrimaryKey
    @ColumnInfo(name = "book_id")
    val bookId: Long,
    @ColumnInfo(name = "target_version")
    val targetVersion: Long,
    @ColumnInfo(name = "local_version")
    val localVersion: Long,
    @ColumnInfo(name = "status")
    val status: String,
    @ColumnInfo(name = "downloaded_words")
    val downloadedWords: Int,
    @ColumnInfo(name = "total_words")
    val totalWords: Int,
    @ColumnInfo(name = "package_sha256")
    val packageSha256: String?,
    @ColumnInfo(name = "last_error")
    val lastError: String?,
    @ColumnInfo(name = "updated_at_ms")
    val updatedAtMs: Long
)

object WordBookContentStatus {
    const val MISSING = "MISSING"
    const val DOWNLOADING = "DOWNLOADING"
    const val READY = "READY"
    const val FAILED = "FAILED"
}
