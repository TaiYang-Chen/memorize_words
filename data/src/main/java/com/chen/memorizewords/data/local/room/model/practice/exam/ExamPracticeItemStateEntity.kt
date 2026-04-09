package com.chen.memorizewords.data.local.room.model.practice.exam

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "exam_practice_item_state")
data class ExamPracticeItemStateEntity(
    @PrimaryKey
    @ColumnInfo(name = "exam_item_id")
    val examItemId: Long,
    @ColumnInfo(name = "favorite")
    val favorite: Boolean = false,
    @ColumnInfo(name = "wrong_book")
    val wrongBook: Boolean = false,
    @ColumnInfo(name = "attempt_count")
    val attemptCount: Int = 0,
    @ColumnInfo(name = "correct_count")
    val correctCount: Int = 0,
    @ColumnInfo(name = "last_result")
    val lastResult: String? = null,
    @ColumnInfo(name = "last_answered_at")
    val lastAnsweredAt: Long? = null
)
