package com.chen.memorizewords.data.practice.local.room.model.practice.session

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "practice_session_word",
    primaryKeys = ["session_id", "sequence"],
    foreignKeys = [
        ForeignKey(
            entity = PracticeSessionRecordEntity::class,
            parentColumns = ["id"],
            childColumns = ["session_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("session_id"),
        Index("word_id")
    ]
)
data class PracticeSessionWordEntity(
    @ColumnInfo(name = "session_id")
    val sessionId: Long,
    @ColumnInfo(name = "sequence")
    val sequence: Int,
    @ColumnInfo(name = "word_id")
    val wordId: Long
)
