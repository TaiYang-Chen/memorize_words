package com.chen.memorizewords.data.local.room.model.study.favorites

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "word_favorite",
    primaryKeys = ["word_id"],
    indices = [Index(value = ["word_id"], unique = true)]
)
data class WordFavoriteEntity(
    @ColumnInfo(name = "word_id")
    val wordId: Long,

    @ColumnInfo(name = "added_date")
    val addedDate: String
)
