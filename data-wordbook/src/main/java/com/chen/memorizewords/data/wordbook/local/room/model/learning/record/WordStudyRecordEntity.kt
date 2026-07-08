package com.chen.memorizewords.data.wordbook.local.room.model.learning.record

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "word_study_records",
    indices = [
        Index(value = ["date", "word_id", "is_new_word"], unique = true),
        Index(value = ["date", "is_new_word"]),
        Index(value = ["date", "id"]),
        Index("word_id")
    ]
)
data class WordStudyRecordEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "date")
    val date: String,

    @ColumnInfo(name = "word")
    val word: String,

    @ColumnInfo(name = "word_id")
    val wordId: Long,

    @ColumnInfo(name = "definition")
    val definition: String,

    @ColumnInfo(name = "is_new_word")
    val isNewWord: Boolean
)
