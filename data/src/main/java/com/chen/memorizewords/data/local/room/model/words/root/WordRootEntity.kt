package com.chen.memorizewords.data.local.room.model.words.root.root

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "word_roots",
    indices = [
        Index(value = ["root_word"], unique = true),
        Index("source_language"),
        Index("difficulty")
    ]
)
data class WordRootEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: Long,

    @ColumnInfo(name = "root_word")
    val rootWord: String,

    @ColumnInfo(name = "core_meaning")
    val coreMeaning: String,

    @ColumnInfo(name = "etymology")
    val etymology: String? = null,

    @ColumnInfo(name = "source_language")
    val sourceLanguage: String,

    @ColumnInfo(name = "difficulty")
    val difficulty: Int = 1,

    @ColumnInfo(name = "tags")
    val tags: String? = null
)
