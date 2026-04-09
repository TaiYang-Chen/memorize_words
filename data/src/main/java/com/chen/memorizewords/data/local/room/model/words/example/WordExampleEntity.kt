package com.chen.memorizewords.data.local.room.model.words.example

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "word_examples",
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

    // ==== 关联信息 ====
    @ColumnInfo(name = "word_id")
    val wordId: Long,  // 关联的单词ID

    @ColumnInfo(name = "definition_id")
    val definitionId: Long? = null,  // 关联的释义ID（可以为空）

    // ==== 例句内容 ====
    @ColumnInfo(name = "english_sentence")
    val englishSentence: String,  // 英文例句

    @ColumnInfo(name = "chinese_translation")
    val chineseTranslation: String? = null,  // 中文翻译

    // ==== 难度和质量 ====
    @ColumnInfo(name = "difficulty_level")
    val difficultyLevel: DifficultyLevel = DifficultyLevel.MEDIUM,  // 例句难度
) {
    enum class DifficultyLevel {
        VERY_EASY, EASY, MEDIUM, HARD, VERY_HARD, EXPERT;

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
