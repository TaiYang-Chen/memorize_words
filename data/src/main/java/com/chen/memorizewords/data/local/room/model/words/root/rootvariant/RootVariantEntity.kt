package com.chen.memorizewords.data.local.room.model.words.root.rootvariant

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 表示词根变体的数据库实体。
 * @property id 唯一标识符。
 * @property rootId 关联的 [WordRootEntity] 的 ID。
 * @property variant 词根的变体形式。
 */
@Entity(
    tableName = "root_variants",
    indices = [Index("rootId")]
)
data class RootVariantEntity(
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
     * 词根的变体形式。
     */
    val variant: String
)
