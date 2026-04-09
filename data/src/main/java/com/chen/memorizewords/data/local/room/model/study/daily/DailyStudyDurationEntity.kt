package com.chen.memorizewords.data.local.room.model.study.daily

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "daily_study_duration")
data class DailyStudyDurationEntity(
    @PrimaryKey
    @ColumnInfo(name = "date")
    val date: String,

    @ColumnInfo(name = "total_duration_ms")
    val totalDurationMs: Long,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,

    @ColumnInfo(name = "is_new_plan_completed")
    val isNewPlanCompleted: Boolean = false,

    @ColumnInfo(name = "is_review_plan_completed")
    val isReviewPlanCompleted: Boolean = false
)
