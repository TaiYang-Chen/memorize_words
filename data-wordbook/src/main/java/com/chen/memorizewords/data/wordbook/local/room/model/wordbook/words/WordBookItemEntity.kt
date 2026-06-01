package com.chen.memorizewords.data.wordbook.local.room.model.wordbook.words

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import com.chen.memorizewords.data.wordbook.local.room.model.wordbook.wordbook.WordBookEntity
import com.chen.memorizewords.data.wordbook.local.room.model.words.word.WordEntity

@Entity(
    tableName = "word_book_words",
    primaryKeys = ["word_book_id", "word_id"],
    foreignKeys = [
        ForeignKey(
            entity = WordBookEntity::class,
            parentColumns = ["id"],
            childColumns = ["word_book_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = WordEntity::class,
            parentColumns = ["id"],
            childColumns = ["word_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
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
