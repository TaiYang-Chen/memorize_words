package com.chen.memorizewords.data.local.room.model.words.definition

import androidx.room.*
import com.chen.memorizewords.domain.model.words.enums.PartOfSpeech

@Entity(
    tableName = "word_definitions"
)
data class WordDefinitionEntity(

    @ColumnInfo(name = "id")
    @PrimaryKey
    val id: Long,

    // ==== 关联信息 ====
    @ColumnInfo(name = "word_id", index = true)
    val wordId: Long,  // 关联的单词ID

    // ==== 释义核心 ====
    @ColumnInfo(name = "part_of_speech")
    val partOfSpeech: PartOfSpeech,  // 词�?

    @ColumnInfo(name = "meaning_chinese")
    val meaningChinese: String  // 中文释义
)



