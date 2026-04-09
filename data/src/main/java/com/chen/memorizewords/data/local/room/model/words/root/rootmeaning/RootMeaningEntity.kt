package com.chen.memorizewords.data.local.room.model.words.root.rootmeaning

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 表示词根含义的数据库实体。
 * @property id 唯一标识符。
 * @property rootId 关联的 [WordRootEntity] 的 ID。
 * @property meaning 具体的含义。
 */
@Entity(
    tableName = "root_meanings",
    indices = [Index("rootId")]
)
data class RootMeaningEntity(
    /**
     * 主键，自动生成。
     */
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: Long,
    /**
     * 外键，关联到 [WordRootEntity] 的主键。
     */
    val rootId: Long,
    /**
     * 词根在这个词性下的具体含义。
     */
    val meaning: String
)
