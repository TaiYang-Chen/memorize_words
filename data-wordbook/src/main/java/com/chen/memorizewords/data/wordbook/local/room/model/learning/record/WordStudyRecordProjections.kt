package com.chen.memorizewords.data.wordbook.local.room.model.learning.record

data class LearningDailyWordStatsProjection(
    val date: String,
    val newCount: Int,
    val reviewCount: Int
)

data class LearningDailyStudyWordRecordProjection(
    val wordId: Long,
    val word: String,
    val definition: String,
    val isNewWord: Boolean
)
