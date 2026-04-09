package com.chen.memorizewords.feature.home.ui.stats

enum class WeeklyWordFilter {
    ALL,
    NEW,
    REVIEW
}

enum class CalendarStudyStatus {
    NONE,
    CHECKED_IN,
    STUDIED,
    NEW_DONE,
    REVIEW_DONE,
    ALL_DONE
}

data class CalendarDayCellUi(
    val date: String,
    val dayText: String,
    val isCurrentMonth: Boolean,
    val isToday: Boolean,
    val isSelected: Boolean,
    val status: CalendarStudyStatus
)

data class CalendarMonthPageUi(
    val monthLabel: String,
    val cells: List<CalendarDayCellUi>
)

data class WeekBarUi(
    val dayLabel: String,
    val valueLabel: String,
    val value: Long
)

data class DayStudyWordItemUi(
    val word: String,
    val definition: String
)

data class DayStudyDetailUi(
    val dateText: String,
    val newCountText: String,
    val reviewCountText: String,
    val durationText: String,
    val planStatusText: String,
    val checkInStatusText: String,
    val showCheckInStatus: Boolean,
    val showCheckInButton: Boolean,
    val makeUpButtonText: String,
    val makeUpButtonEnabled: Boolean,
    val isEmptyDay: Boolean,
    val newWords: List<DayStudyWordItemUi>,
    val reviewWords: List<DayStudyWordItemUi>
)
