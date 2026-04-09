package com.chen.memorizewords.data.local.room.model.study.daily

data class DailyWordStatsProjection(
    val date: String,
    val newCount: Int,
    val reviewCount: Int
)

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

data class DailyStudyWordRecordProjection(
    val wordId: Long,
    val word: String,
    val definition: String,
    val isNewWord: Boolean
)

data class DailyStudySummaryProjection(
    val durationMs: Long,
    val isNewPlanCompleted: Boolean,
    val isReviewPlanCompleted: Boolean
)
