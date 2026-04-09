package com.chen.memorizewords.data.local.room.model.study.progress.wordbook

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "word_book_progress")
data class WordBookProgressEntity(
    @PrimaryKey
    @ColumnInfo(name = "book_id")
    val wordBookId: Long,

    @ColumnInfo(name = "learnedCount")
    val learnedCount: Int = 0,

    @ColumnInfo(name = "masteredCount")
    val masteredCount: Int = 0,

    @ColumnInfo(name = "correct_count")
    val correctCount: Int = 0,

    @ColumnInfo(name = "wrong_count")
    val wrongCount: Int = 0,

    @ColumnInfo(name = "study_day_count")
    val studyDayCount: Int = 0,

    @ColumnInfo(name = "last_study_date")
    val lastStudyDate: String = ""
)
