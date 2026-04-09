package com.chen.memorizewords.domain.model.study.record

data class DailyWordStats(
    val date: String,
    val newCount: Int,
    val reviewCount: Int
)

data class DailyDurationStats(
    val date: String,
    val durationMs: Long
)

data class CalendarDayStats(
    val date: String,
    val hasStudy: Boolean,
    val hasCheckIn: Boolean,
    val isNewPlanCompleted: Boolean,
    val isReviewPlanCompleted: Boolean
)

data class DailyStudyWordRecord(
    val wordId: Long,
    val word: String,
    val definition: String,
    val isNewWord: Boolean
)

data class DailyStudySummary(
    val date: String,
    val durationMs: Long,
    val isNewPlanCompleted: Boolean,
    val isReviewPlanCompleted: Boolean
)

data class DailyStudyDetail(
    val date: String,
    val newCount: Int,
    val reviewCount: Int,
    val durationMs: Long,
    val hasStudy: Boolean,
    val isNewPlanCompleted: Boolean,
    val isReviewPlanCompleted: Boolean,
    val newWords: List<DailyStudyWordRecord>,
    val reviewWords: List<DailyStudyWordRecord>,
    val checkInRecord: CheckInRecord? = null,
    val canMakeUp: Boolean = false,
    val availableMakeupCardCount: Int? = null
)
