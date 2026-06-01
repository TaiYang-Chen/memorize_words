package com.chen.memorizewords.data.word.local.room.model.words.root.rootvariant

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.chen.memorizewords.data.word.local.room.model.words.root.root.WordRootEntity

@Entity(
    tableName = "root_variants",
    foreignKeys = [
        ForeignKey(
            entity = WordRootEntity::class,
            parentColumns = ["id"],
            childColumns = ["root_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("root_id")]
)
data class RootVariantEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: Long,
    @ColumnInfo(name = "root_id")
    val rootId: Long,
    val variant: String
)
