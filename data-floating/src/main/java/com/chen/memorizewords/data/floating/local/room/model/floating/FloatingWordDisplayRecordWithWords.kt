package com.chen.memorizewords.data.floating.local.room.model.floating

import androidx.room.Embedded
import androidx.room.Relation

data class FloatingWordDisplayRecordWithWords(
    @Embedded
    val record: FloatingWordDisplayRecordEntity,
    @Relation(parentColumn = "date", entityColumn = "record_date")
    val words: List<FloatingWordDisplayWordEntity>
)
