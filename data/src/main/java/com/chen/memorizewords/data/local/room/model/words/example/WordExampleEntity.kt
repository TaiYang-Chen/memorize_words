package com.chen.memorizewords.data.local.room.model.words.example

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.chen.memorizewords.data.local.room.model.words.definition.WordDefinitionEntity
import com.chen.memorizewords.data.local.room.model.words.word.WordEntity

@Entity(
    tableName = "word_examples",
    foreignKeys = [
        ForeignKey(
            entity = WordEntity::class,
            parentColumns = ["id"],
            childColumns = ["word_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = WordDefinitionEntity::class,
            parentColumns = ["id"],
            childColumns = ["definition_id"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index("word_id"),
        Index("definition_id"),
        Index("difficulty_level")
    ]
)
data class WordExampleEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: Long,
    @ColumnInfo(name = "word_id")
    val wordId: Long,
    @ColumnInfo(name = "definition_id")
    val definitionId: Long? = null,
    @ColumnInfo(name = "english_sentence")
    val englishSentence: String,
    @ColumnInfo(name = "chinese_translation")
    val chineseTranslation: String? = null,
    @ColumnInfo(name = "difficulty_level")
    val difficultyLevel: DifficultyLevel = DifficultyLevel.MEDIUM,
) {
    enum class DifficultyLevel {
        VERY_EASY,
        EASY,
        MEDIUM,
        HARD,
        VERY_HARD,
        EXPERT;

        companion object {
            fun fromInt(value: Int): DifficultyLevel {
                return when (value) {
                    1 -> VERY_EASY
                    2 -> EASY
                    3 -> MEDIUM
                    4 -> HARD
                    5 -> VERY_HARD
                    6 -> EXPERT
                    else -> MEDIUM
                }
            }
        }
    }
}
