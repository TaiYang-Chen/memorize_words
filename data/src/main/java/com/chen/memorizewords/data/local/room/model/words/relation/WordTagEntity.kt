package com.chen.memorizewords.data.local.room.model.words.relation

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import com.chen.memorizewords.data.local.room.model.words.word.WordEntity

@Entity(
    tableName = "word_tags",
    primaryKeys = ["word_id", "value"],
    foreignKeys = [
        ForeignKey(
            entity = WordEntity::class,
            parentColumns = ["id"],
            childColumns = ["word_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("word_id"),
        Index("normalized_value")
    ]
)
data class WordTagEntity(
    @ColumnInfo(name = "word_id")
    val wordId: Long,

    @ColumnInfo(name = "value")
    val value: String,

    @ColumnInfo(name = "normalized_value")
    val normalizedValue: String
)
