package com.chen.memorizewords.data.local.room.model.wordbook.words

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "word_book_words",
    primaryKeys = ["word_book_id", "word_id"],
    indices = [
        Index("word_book_id"),
        Index("word_id")
    ]
)
data class WordBookItemEntity(
    @ColumnInfo(name = "word_book_id")
    val wordBookId: Long,
    @ColumnInfo(name = "word_id")
    val wordId: Long
)
