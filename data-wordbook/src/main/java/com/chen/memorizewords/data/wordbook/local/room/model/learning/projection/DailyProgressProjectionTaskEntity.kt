package com.chen.memorizewords.data.wordbook.local.room.model.learning.projection

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "daily_progress_projection_task",
    indices = [
        Index(value = ["client_sequence"], unique = true),
        Index(value = ["business_date", "client_sequence"])
    ]
)
data class DailyProgressProjectionTaskEntity(
    @PrimaryKey
    @ColumnInfo(name = "client_event_id")
    val clientEventId: String,

    @ColumnInfo(name = "client_sequence")
    val clientSequence: Long,

    @ColumnInfo(name = "business_date")
    val businessDate: String,

    @ColumnInfo(name = "new_count_after")
    val newCountAfter: Int,

    @ColumnInfo(name = "review_count_after")
    val reviewCountAfter: Int,

    @ColumnInfo(name = "daily_new_target")
    val dailyNewTarget: Int,

    @ColumnInfo(name = "daily_review_target")
    val dailyReviewTarget: Int,

    @ColumnInfo(name = "created_at_ms")
    val createdAtMs: Long
)
