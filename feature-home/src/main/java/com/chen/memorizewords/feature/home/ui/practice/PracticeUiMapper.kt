package com.chen.memorizewords.feature.home.ui.practice

import com.chen.memorizewords.core.common.resource.ResourceProvider
import com.chen.memorizewords.domain.model.practice.PracticeDailyDurationStats
import com.chen.memorizewords.domain.model.practice.PracticeEntryType
import com.chen.memorizewords.domain.model.practice.PracticeMode
import com.chen.memorizewords.domain.model.practice.PracticeSessionRecord
import com.chen.memorizewords.feature.home.R
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import kotlin.math.roundToInt

class PracticeUiMapper @Inject constructor(
    private val resourceProvider: ResourceProvider
) {

    fun formatIncreasePercent(stats: List<PracticeDailyDurationStats>): String {
        val today = stats.lastOrNull()?.durationMs ?: 0L
        val yesterday = stats.getOrNull(stats.size - 2)?.durationMs ?: 0L
        if (yesterday <= 0L) {
            return if (today <= 0L) "0%" else "100%+"
        }
        val percent = ((today - yesterday).toDouble() * 100.0 / yesterday.toDouble())
        val rounded = percent.roundToInt()
        return if (rounded >= 0) "+$rounded%" else "$rounded%"
    }

    fun formatDuration(durationMs: Long): String {
        val totalMinutes = (durationMs / 60_000L).coerceAtLeast(0L)
        val hours = totalMinutes / 60L
        val minutes = totalMinutes % 60L
        return if (hours > 0L) {
            if (minutes > 0L) {
                resourceProvider.getString(R.string.home_duration_hours_minutes, hours, minutes)
            } else {
                resourceProvider.getString(R.string.home_duration_hours, hours)
            }
        } else {
            resourceProvider.getString(R.string.home_duration_minutes, minutes)
        }
    }

    fun formatDurationHms(durationMs: Long): String {
        val totalSeconds = (durationMs / 1000L).coerceAtLeast(0L)
        val hours = totalSeconds / 3600L
        val minutes = (totalSeconds % 3600L) / 60L
        val seconds = totalSeconds % 60L
        return String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
    }

    fun formatDurationMinutes(durationMs: Long): String {
        val totalMinutes = (durationMs / 60_000L).coerceAtLeast(0L)
        return totalMinutes.toString()
    }

    fun buildSessionUi(records: List<PracticeSessionRecord>): List<PracticeSessionRecordUi> {
        if (records.isEmpty()) return emptyList()
        return records.map { record ->
            PracticeSessionRecordUi(
                id = record.id,
                titleText = buildRecordTitle(record),
                subtitleText = buildRecordSubtitle(record),
                iconRes = recordIconRes(record.mode),
                iconTintRes = recordIconTint(record.mode)
            )
        }
    }

    private fun buildRecordTitle(record: PracticeSessionRecord): String {
        val modeLabel = when (record.mode) {
            PracticeMode.LISTENING -> resourceProvider.getString(R.string.home_practice_mode_listening)
            PracticeMode.SHADOWING -> resourceProvider.getString(R.string.home_practice_mode_shadowing)
            PracticeMode.SPELLING -> resourceProvider.getString(R.string.home_practice_mode_spelling)
            PracticeMode.AUDIO_LOOP -> resourceProvider.getString(R.string.home_practice_mode_audio_loop)
            PracticeMode.EXAM -> resourceProvider.getString(R.string.home_practice_mode_exam)
        }
        val entryLabel = when (record.entryType) {
            PracticeEntryType.SELF -> resourceProvider.getString(
                R.string.home_practice_entry_self,
                record.entryCount
            )
            PracticeEntryType.RANDOM -> resourceProvider.getString(
                R.string.home_practice_entry_random,
                record.entryCount
            )
        }
        return resourceProvider.getString(R.string.home_practice_record_title, modeLabel, entryLabel)
    }

    private fun buildRecordSubtitle(record: PracticeSessionRecord): String {
        val week = practiceRecordWeekLabel(
            recordDate = record.date,
            createdAt = record.createdAt
        )
        val time = practiceRecordClockText(record.createdAt, Locale.getDefault())
        val duration = formatDuration(record.durationMs)
        val modeLabel = when (record.mode) {
            PracticeMode.LISTENING -> resourceProvider.getString(R.string.practice_record_mode_listening)
            PracticeMode.SHADOWING -> resourceProvider.getString(R.string.practice_record_mode_shadowing)
            PracticeMode.SPELLING -> resourceProvider.getString(R.string.practice_record_mode_spelling)
            PracticeMode.AUDIO_LOOP -> resourceProvider.getString(R.string.practice_record_mode_audio_loop)
            PracticeMode.EXAM -> resourceProvider.getString(R.string.practice_record_mode_exam)
        }
        val base = listOf(week, time, duration, modeLabel).joinToString(PRACTICE_RECORD_SEPARATOR)
        if ((record.mode != PracticeMode.SPELLING && record.mode != PracticeMode.EXAM) || record.questionCount <= 0) return base
        return listOf(base, practiceRecordSpellingSummaryBrief(record))
            .joinToString(PRACTICE_RECORD_SEPARATOR)
    }

    private fun recordIconRes(mode: PracticeMode): Int {
        return when (mode) {
            PracticeMode.LISTENING -> R.drawable.feature_home_ic_practice_headset
            PracticeMode.SHADOWING -> R.drawable.feature_home_ic_practice_mic
            PracticeMode.SPELLING -> R.drawable.feature_home_ic_practice_edit
            PracticeMode.AUDIO_LOOP -> R.drawable.feature_home_ic_practice_play
            PracticeMode.EXAM -> R.drawable.feature_home_ic_practice_exam
        }
    }

    private fun recordIconTint(mode: PracticeMode): Int {
        return when (mode) {
            PracticeMode.LISTENING -> R.color.practice_record_icon_listening
            PracticeMode.SHADOWING -> R.color.practice_record_icon_shadowing
            PracticeMode.SPELLING -> R.color.practice_record_icon_spelling
            PracticeMode.AUDIO_LOOP -> R.color.practice_record_icon_audio_loop
            PracticeMode.EXAM -> R.color.practice_record_icon_exam
        }
    }

    private fun practiceRecordWeekLabel(
        recordDate: String,
        createdAt: Long,
        locale: Locale = Locale.getDefault()
    ): String {
        val calendar = Calendar.getInstance()
        val parsedDate = runCatching {
            SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(recordDate)
        }.getOrNull()
        if (parsedDate != null) {
            calendar.time = parsedDate
        } else {
            calendar.timeInMillis = createdAt
        }
        val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
        val dayCalendar = Calendar.getInstance().apply { set(Calendar.DAY_OF_WEEK, dayOfWeek) }
        return SimpleDateFormat("EEE", locale).format(dayCalendar.time)
    }

    private fun practiceRecordClockText(
        createdAt: Long,
        locale: Locale = Locale.getDefault()
    ): String {
        return SimpleDateFormat("HH:mm", locale).format(Date(createdAt))
    }

    private fun practiceRecordSpellingSummaryBrief(record: PracticeSessionRecord): String {
        if ((record.mode != PracticeMode.SPELLING && record.mode != PracticeMode.EXAM) || record.questionCount <= 0) return ""
        val completionRate = if (record.questionCount > 0) {
            (record.completedCount * 100) / record.questionCount
        } else {
            0
        }
        val settledAccuracyText = if (record.completedCount > 0) {
            resourceProvider.getString(
                R.string.practice_record_percentage,
                (record.correctCount * 100) / record.completedCount
            )
        } else {
            resourceProvider.getString(R.string.practice_record_not_available)
        }
        return resourceProvider.getString(
            R.string.practice_record_spelling_summary_brief,
            completionRate,
            settledAccuracyText
        )
    }

    private companion object {
        private const val PRACTICE_RECORD_SEPARATOR = " 璺?"
    }
}
