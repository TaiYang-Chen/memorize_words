package com.chen.memorizewords.feature.home.ui.practice

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private const val PRACTICE_RECORD_DATE_PATTERN = "yyyy-MM-dd"
private const val PRACTICE_RECORD_TIME_PATTERN = "HH:mm"

internal fun resolvePracticeRecordWeekLabel(
    recordDate: String,
    createdAt: Long,
    locale: Locale = Locale.getDefault()
): String {
    val calendar = Calendar.getInstance()
    val parsedDate = runCatching {
        SimpleDateFormat(PRACTICE_RECORD_DATE_PATTERN, Locale.US).parse(recordDate)
    }.getOrNull()
    if (parsedDate != null) {
        calendar.time = parsedDate
    } else {
        calendar.timeInMillis = createdAt
    }
    return resolvePracticeWeekLabel(calendar.get(Calendar.DAY_OF_WEEK), locale)
}

internal fun formatPracticeRecordClockText(
    createdAt: Long,
    locale: Locale = Locale.getDefault()
): String {
    return SimpleDateFormat(PRACTICE_RECORD_TIME_PATTERN, locale).format(Date(createdAt))
}

internal fun resolvePracticeWeekLabel(
    dayOfWeek: Int,
    locale: Locale = Locale.getDefault()
): String {
    return when (dayOfWeek) {
        Calendar.SUNDAY,
        Calendar.MONDAY,
        Calendar.TUESDAY,
        Calendar.WEDNESDAY,
        Calendar.THURSDAY,
        Calendar.FRIDAY,
        Calendar.SATURDAY -> {
            val calendar = Calendar.getInstance().apply {
                set(Calendar.DAY_OF_WEEK, dayOfWeek)
            }
            SimpleDateFormat("EEE", locale).format(calendar.time)
        }

        else -> SimpleDateFormat("EEE", locale).format(Date())
    }
}
