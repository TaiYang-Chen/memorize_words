package com.chen.memorizewords.data.wordbook.local.room.model.study.progress.wordbook

data class WordBookProgressSummary(
    val wordBookId: Long,
    val learningCount: Int,
    val masteredCount: Int,
    val correctCount: Int,
    val wrongCount: Int,
    val studyDayCount: Int,
    val lastStudyDate: String?,
    val revision: Long
)
