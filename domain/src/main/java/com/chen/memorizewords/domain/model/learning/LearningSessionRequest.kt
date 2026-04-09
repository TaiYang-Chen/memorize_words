package com.chen.memorizewords.domain.model.learning

data class LearningSessionRequest(
    val initialLearnedCount: Int = 0,
    val wordIds: List<Long>,
    val sessionType: Int,
    val sessionWordCount: Int
)
