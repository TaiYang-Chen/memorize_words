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

data class StatsOverviewCardUi(
    val value: String,
    val unit: String,
    val title: String,
    val changeText: String,
    val iconResId: Int,
    val iconBackgroundResId: Int
)

data class StatsTrendPointUi(
    val dayLabel: String,
    val durationHours: Float,
    val newWordCount: Int
)

data class StatsTimeDistributionUi(
    val label: String,
    val percent: Int,
    val color: Int
)

data class StatsAchievementUi(
    val title: String,
    val subtitle: String,
    val iconResId: Int,
    val achieved: Boolean
)

data class StatsReportRowUi(
    val label: String,
    val value: String,
    val unit: String,
    val iconResId: Int,
    val iconBackgroundResId: Int
)

data class DayStudyWordItemUi(
    val word: String,
    val definition: String,
    val badgeText: String
)

data class DayStudyDetailUi(
    val dateText: String,
    val newCountValue: String,
    val reviewCountValue: String,
    val durationValue: String,
    val durationUnit: String,
    val planStatusText: String,
    val planStatusSubtitle: String,
    val checkInStatusText: String,
    val showCheckInStatus: Boolean,
    val showCheckInButton: Boolean,
    val makeUpButtonText: String,
    val makeUpButtonEnabled: Boolean,
    val isEmptyDay: Boolean,
    val newWordsTitle: String,
    val reviewWordsTitle: String,
    val newWords: List<DayStudyWordItemUi>,
    val reviewWords: List<DayStudyWordItemUi>,
    val showNewWordsMore: Boolean,
    val showReviewWordsMore: Boolean,
    val newWordsMoreText: String,
    val reviewWordsMoreText: String
)
