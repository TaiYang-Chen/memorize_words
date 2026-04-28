package com.chen.memorizewords.data.local.room.model.study.favorites

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import com.chen.memorizewords.data.local.room.model.words.word.WordEntity

@Entity(
    tableName = "word_favorite",
    primaryKeys = ["word_id"],
    indices = [
        Index("added_at")
    ],
    foreignKeys = [
        ForeignKey(
            entity = WordEntity::class,
            parentColumns = ["id"],
            childColumns = ["word_id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class WordFavoriteEntity(
    @ColumnInfo(name = "word_id")
    val wordId: Long,
    @ColumnInfo(name = "added_at")
    val addedAt: Long
)
