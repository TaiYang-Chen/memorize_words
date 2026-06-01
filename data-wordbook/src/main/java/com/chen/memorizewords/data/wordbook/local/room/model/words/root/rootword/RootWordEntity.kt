package com.chen.memorizewords.data.wordbook.local.room.model.words.root.rootword

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import com.chen.memorizewords.data.wordbook.local.room.model.words.root.root.WordRootEntity
import com.chen.memorizewords.data.wordbook.local.room.model.words.word.WordEntity

@Entity(
    tableName = "word_root_relation",
    primaryKeys = ["word_id", "sequence"],
    foreignKeys = [
        ForeignKey(
            entity = WordEntity::class,
            parentColumns = ["id"],
            childColumns = ["word_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = WordRootEntity::class,
            parentColumns = ["id"],
            childColumns = ["root_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["word_id"]),
        Index(value = ["root_id"])
    ]
)
data class RootWordEntity(
    @ColumnInfo(name = "word_id")
    val wordId: Long,
    @ColumnInfo(name = "root_id")
    val rootId: Long,
    val context: String,
    @ColumnInfo(name = "part_of_speech")
    val partOfSpeech: String,
    val sequence: Int
)
