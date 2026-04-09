package com.chen.memorizewords.data.local.room.model.words.relation

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "word_associations",
    primaryKeys = ["word_id", "value"],
    indices = [
        Index("word_id"),
        Index("normalized_value")
    ]
)
data class WordAssociationEntity(
    @ColumnInfo(name = "word_id")
    val wordId: Long,

    @ColumnInfo(name = "value")
    val value: String,

    @ColumnInfo(name = "normalized_value")
    val normalizedValue: String
)
