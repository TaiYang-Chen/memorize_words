package com.chen.memorizewords.data.study.local.room.model.study.daily

data class DailyDurationStatsProjection(
    val date: String,
    val durationMs: Long
)

data class CalendarDayStatsProjection(
    val date: String,
    val hasStudy: Boolean,
    val hasCheckIn: Boolean,
    val isNewPlanCompleted: Boolean,
    val isReviewPlanCompleted: Boolean
)

data class DailyStudySummaryProjection(
    val durationMs: Long,
    val isNewPlanCompleted: Boolean,
    val isReviewPlanCompleted: Boolean
)
