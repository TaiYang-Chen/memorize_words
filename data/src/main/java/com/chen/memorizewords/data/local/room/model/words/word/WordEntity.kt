package com.chen.memorizewords.data.local.room.model.words.word

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "words",
    indices = [Index(value = ["normalized_word"], unique = true)]
)
data class WordEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: Long,

    @ColumnInfo(name = "word")
    val word: String,

    @ColumnInfo(name = "normalized_word")
    val normalizedWord: String = word.lowercase().trim(),

    @ColumnInfo(name = "phonetic_us")
    val phoneticUS: String? = null,

    @ColumnInfo(name = "phonetic_uk")
    val phoneticUK: String? = null,

    @ColumnInfo(name = "has_irregular_forms")
    val hasIrregularForms: Boolean = false,

    @ColumnInfo(name = "word_family")
    val wordFamily: String? = null
)
