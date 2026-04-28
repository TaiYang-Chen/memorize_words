package com.chen.memorizewords.data.local.room.model.floating

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "floating_word_display_word",
    primaryKeys = ["record_date", "sequence"],
    foreignKeys = [
        ForeignKey(
            entity = FloatingWordDisplayRecordEntity::class,
            parentColumns = ["date"],
            childColumns = ["record_date"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("record_date"),
        Index("word_id")
    ]
)
data class FloatingWordDisplayWordEntity(
    @ColumnInfo(name = "record_date")
    val recordDate: String,
    @ColumnInfo(name = "sequence")
    val sequence: Int,
    @ColumnInfo(name = "word_id")
    val wordId: Long
)
