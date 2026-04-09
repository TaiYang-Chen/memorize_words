package com.chen.memorizewords.data.local.room.model.words.root.root

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "root_tags",
    primaryKeys = ["root_id", "value"],
    indices = [
        Index("root_id"),
        Index("normalized_value")
    ]
)
data class RootTagEntity(
    @ColumnInfo(name = "root_id")
    val rootId: Long,

    @ColumnInfo(name = "value")
    val value: String,

    @ColumnInfo(name = "normalized_value")
    val normalizedValue: String
)
