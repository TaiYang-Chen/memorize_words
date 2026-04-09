package com.chen.memorizewords.data.local.room.model.study.daily

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "word_study_records",
    indices = [Index(value = ["date", "word_id", "is_new_word"], unique = true)]
)
data class WordStudyRecordsEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "date")
    val date: String,  // 日期，格式如 "2026-01-07"
    @ColumnInfo(name = "word")
    val word: String,  // 单词
    @ColumnInfo(name = "word_id")
    val wordId: Long,  // 单词
    @ColumnInfo(name = "definition")
    val definition: String,  // 单词释义
    @ColumnInfo(name = "is_new_word")
    val isNewWord: Boolean,  // 类别：是否新学，不是就是复习
)