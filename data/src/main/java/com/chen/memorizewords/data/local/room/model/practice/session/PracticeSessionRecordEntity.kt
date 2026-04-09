package com.chen.memorizewords.data.local.room.model.practice.session

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "practice_session_record",
    indices = [
        Index("date"),
        Index("created_at")
    ]
)
data class PracticeSessionRecordEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val date: String,
    val mode: String,
    @ColumnInfo(name = "entry_type")
    val entryType: String,
    @ColumnInfo(name = "entry_count")
    val entryCount: Int,
    @ColumnInfo(name = "duration_ms")
    val durationMs: Long,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "word_ids")
    val wordIds: List<Long>,
    @ColumnInfo(name = "question_count")
    val questionCount: Int = 0,
    @ColumnInfo(name = "completed_count")
    val completedCount: Int = 0,
    @ColumnInfo(name = "correct_count")
    val correctCount: Int = 0,
    @ColumnInfo(name = "submit_count")
    val submitCount: Int = 0
)
