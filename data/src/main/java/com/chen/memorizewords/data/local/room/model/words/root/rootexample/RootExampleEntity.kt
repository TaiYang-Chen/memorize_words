package com.chen.memorizewords.data.local.room.model.words.root.rootexample

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 表示词根例词示例的数据库实体。
 * @property id 唯一标识符。
 * @property meaningId 关联的 [RootMeaningEntity] 的 ID。
 * @property exampleSentence 词根例词。
 * @property translation 词根例词的翻译。
 */
@Entity(
    tableName = "root_examples",
    indices = [Index("meaningId")]
)
data class RootExampleEntity(
    /**
     * 主键，自动生成。
     */
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: Long,
    /**
     * 外键，关联到 [RootMeaningEntity] 的主键。
     */
    val meaningId: Long,
    /**
     * 包含词根用法的示例句子。
     */
    val exampleSentence: String,
    /**
     * 示例句子的中文翻译。
     */
    val translation: String
)
