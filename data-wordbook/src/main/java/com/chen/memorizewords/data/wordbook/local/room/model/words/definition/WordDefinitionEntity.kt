package com.chen.memorizewords.data.wordbook.local.room.model.words.definition

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.chen.memorizewords.data.wordbook.local.room.model.words.word.WordEntity
import com.chen.memorizewords.domain.word.model.enums.PartOfSpeech

@Entity(
    tableName = "word_definitions",
    foreignKeys = [
        ForeignKey(
            entity = WordEntity::class,
            parentColumns = ["id"],
            childColumns = ["word_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("word_id")
    ]
)
data class WordDefinitionEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: Long,
    @ColumnInfo(name = "word_id")
    val wordId: Long,
    @ColumnInfo(name = "part_of_speech")
    val partOfSpeech: PartOfSpeech,
    @ColumnInfo(name = "meaning_chinese")
    val meaningChinese: String
)
