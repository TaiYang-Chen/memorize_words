package com.chen.memorizewords.feature.home.ui.practice

internal const val PRACTICE_DAILY_GOAL_SECONDS = 30 * 60L
internal const val PRACTICE_SECONDS_PER_MINUTE = 60L
internal const val PRACTICE_MINUTES_PER_HOUR = 60L
internal const val PRACTICE_XP_PER_LEVEL = 100
internal const val PRACTICE_WEEK_DAYS = 7
internal const val PRACTICE_COMPARISON_DAYS = PRACTICE_WEEK_DAYS * 2

data class PracticeDashboardUi(
    val todayDurationValue: String = "0",
    val todayDurationUnit: String = "",
    val todayProgressText: String = "",
    val todayPercentText: String = "0%",
    val todayProgress: Int = 0,
    val continuousDaysText: String = "0",
    val weekDurationValue: String = "0",
    val weekDurationUnit: String = "",
    val weekTrendText: String = "0%",
    val weekTrendDirection: PracticeTrendDirection = PracticeTrendDirection.FLAT,
    val levelText: String = "Lv.1",
    val xpText: String = "0 / 100 XP",
    val xpProgress: Int = 0
)

enum class PracticeTrendDirection {
    UP,
    DOWN,
    FLAT
}

data class PracticeDurationParts(
    val value: String,
    val unit: String
)

data class PracticeLevelUi(
    val levelText: String,
    val xpText: String,
    val xpProgress: Int
)
