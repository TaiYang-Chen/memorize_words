package com.chen.memorizewords.domain.study.repository

data class StudyDailyDurationSnapshot(
    val date: String,
    val totalDurationMs: Long,
    val updatedAtMs: Long,
    val isNewPlanCompleted: Boolean,
    val isReviewPlanCompleted: Boolean
)
