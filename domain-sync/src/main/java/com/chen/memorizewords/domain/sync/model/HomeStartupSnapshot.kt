package com.chen.memorizewords.domain.sync.model

import com.chen.memorizewords.domain.wordbook.model.WordBook
import com.chen.memorizewords.domain.wordbook.model.study.StudyPlan
import com.chen.memorizewords.domain.wordbook.model.study.progress.wordbook.WordBookProgress

data class HomeStartupSnapshot(
    val userId: Long,
    val businessDate: String,
    val serverTime: Long = 0L,
    val capturedAtMs: Long = 0L,
    val currentWordBook: WordBook? = null,
    val currentWordBookProgress: WordBookProgress? = null,
    val studyPlan: StudyPlan? = null,
    val todayNewWordCount: Int = 0,
    val todayReviewWordCount: Int = 0,
    val todayStudyDurationMs: Long = 0L,
    val continuousCheckInDays: Int = 0,
    val totalStudyDayCount: Int = 0
)
