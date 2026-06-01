package com.chen.memorizewords.data.wordbook.local.room.model.words.root.rootexample

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.chen.memorizewords.data.wordbook.local.room.model.words.root.rootmeaning.RootMeaningEntity

@Entity(
    tableName = "root_examples",
    foreignKeys = [
        ForeignKey(
            entity = RootMeaningEntity::class,
            parentColumns = ["id"],
            childColumns = ["meaning_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("meaning_id")]
)
data class RootExampleEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: Long,
    @ColumnInfo(name = "meaning_id")
    val meaningId: Long,
    val exampleSentence: String,
    val translation: String
)
