package com.chen.memorizewords.data.study.local.room.model.study.favorites

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "word_favorite",
    primaryKeys = ["word_id"],
    indices = [
        Index("added_at")
    ]
)
data class WordFavoriteEntity(
    @ColumnInfo(name = "word_id")
    val wordId: Long,
    @ColumnInfo(name = "word")
    val word: String = "",
    @ColumnInfo(name = "definitions")
    val definitions: String = "",
    @ColumnInfo(name = "phonetic")
    val phonetic: String? = null,
    @ColumnInfo(name = "added_date")
    val addedDate: String = "",
    @ColumnInfo(name = "added_at")
    val addedAt: Long
)