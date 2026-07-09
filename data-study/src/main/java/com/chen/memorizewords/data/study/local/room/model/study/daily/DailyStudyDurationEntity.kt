package com.chen.memorizewords.data.study.local.room.model.study.daily

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

    @ColumnInfo(name = "updated_at_ms")
    val updatedAtMs: Long,

    @ColumnInfo(name = "is_new_plan_completed")
    val isNewPlanCompleted: Boolean = false,

    @ColumnInfo(name = "is_review_plan_completed")
    val isReviewPlanCompleted: Boolean = false
) {
    init {
        require(totalDurationMs >= 0L) { "totalDurationMs must be non-negative" }
        require(updatedAtMs >= 0L) { "updatedAtMs must be non-negative" }
    }
}
