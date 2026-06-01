package com.chen.memorizewords.data.practice.local.room.model.practice.exam

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import com.chen.memorizewords.domain.practice.model.ExamItemLastResult

@Entity(
    tableName = "exam_practice_item_state",
    foreignKeys = [
        ForeignKey(
            entity = ExamPracticeItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["exam_item_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
)
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
    val lastResult: ExamItemLastResult? = null,
    @ColumnInfo(name = "last_answered_at")
    val lastAnsweredAt: Long? = null
) {
    init {
        require(attemptCount >= 0) { "attemptCount must be non-negative" }
        require(correctCount >= 0) { "correctCount must be non-negative" }
        require(lastAnsweredAt == null || lastAnsweredAt >= 0L) { "lastAnsweredAt must be non-negative" }
    }
}
