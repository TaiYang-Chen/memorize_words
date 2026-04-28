package com.chen.memorizewords.data.local.room.model.study.progress.wordbook

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import com.chen.memorizewords.data.local.room.model.wordbook.wordbook.WordBookEntity

@Entity(
    tableName = "word_book_progress",
    foreignKeys = [
        ForeignKey(
            entity = WordBookEntity::class,
            parentColumns = ["id"],
            childColumns = ["book_id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class WordBookProgressEntity(
    @PrimaryKey
    @ColumnInfo(name = "book_id")
    val wordBookId: Long,

    @ColumnInfo(name = "correct_count")
    val correctCount: Int = 0,

    @ColumnInfo(name = "wrong_count")
    val wrongCount: Int = 0,

    @ColumnInfo(name = "study_day_count")
    val studyDayCount: Int = 0,

    @ColumnInfo(name = "last_study_date")
    val lastStudyDate: String? = null
) {
    init {
        require(correctCount >= 0) { "correctCount must be non-negative" }
        require(wrongCount >= 0) { "wrongCount must be non-negative" }
        require(studyDayCount >= 0) { "studyDayCount must be non-negative" }
        require(lastStudyDate?.isNotBlank() != false) { "lastStudyDate cannot be blank" }
    }
}
