package com.chen.memorizewords.data.local.room.model.floating

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "floating_word_display_record")
data class FloatingWordDisplayRecordEntity(
    @PrimaryKey
    val date: String,
    @ColumnInfo(name = "display_count")
    val displayCount: Int,
    @ColumnInfo(name = "word_ids")
    val wordIds: List<Long>,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long
)
