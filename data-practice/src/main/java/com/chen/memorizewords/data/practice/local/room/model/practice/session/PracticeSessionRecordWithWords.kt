package com.chen.memorizewords.data.practice.local.room.model.practice.session

import androidx.room.Embedded
import androidx.room.Relation

data class PracticeSessionRecordWithWords(
    @Embedded
    val record: PracticeSessionRecordEntity,
    @Relation(parentColumn = "id", entityColumn = "session_id")
    val words: List<PracticeSessionWordEntity>
)
