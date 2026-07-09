package com.chen.memorizewords.data.practice.local.room.model.practice.daily

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "daily_practice_duration")
data class DailyPracticeDurationEntity(
    @PrimaryKey
    val date: String,
    @ColumnInfo(name = "total_duration_ms")
    val totalDurationMs: Long,
    @ColumnInfo(name = "updated_at_ms")
    val updatedAtMs: Long
) {
    init {
        require(totalDurationMs >= 0L) { "totalDurationMs must be non-negative" }
        require(updatedAtMs >= 0L) { "updatedAtMs must be non-negative" }
    }
}
