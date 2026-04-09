package com.chen.memorizewords.data.local.room.model.practice.exam

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "exam_practice_word_meta")
data class ExamPracticeWordMetaEntity(
    @PrimaryKey
    @ColumnInfo(name = "word_id")
    val wordId: Long,
    @ColumnInfo(name = "word")
    val word: String,
    @ColumnInfo(name = "total_count")
    val totalCount: Int = 0,
    @ColumnInfo(name = "favorite_count")
    val favoriteCount: Int = 0,
    @ColumnInfo(name = "wrong_count")
    val wrongCount: Int = 0,
    @ColumnInfo(name = "objective_count")
    val objectiveCount: Int = 0,
    @ColumnInfo(name = "cached_at")
    val cachedAt: Long
)
