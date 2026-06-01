package com.chen.memorizewords.data.floating.local.room.model.floating

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "floating_word_display_record")
data class FloatingWordDisplayRecordEntity(
    @PrimaryKey
    val date: String,
    @ColumnInfo(name = "display_count")
    val displayCount: Int,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long
) {
    init {
        require(displayCount >= 0) { "displayCount must be non-negative" }
        require(updatedAt >= 0L) { "updatedAt must be non-negative" }
    }
}
