package com.chen.memorizewords.feature.home.ui.practice

import com.chen.memorizewords.core.common.resource.ResourceProvider
import com.chen.memorizewords.domain.model.practice.PracticeMode
import com.chen.memorizewords.domain.model.practice.PracticeSessionRecord
import com.chen.memorizewords.feature.home.R

internal data class PracticeCompletionMetrics(
    val completionRate: Int,
    val settledAccuracyRate: Int?
)

internal const val PRACTICE_RECORD_SEPARATOR = " 路 "

internal fun PracticeSessionRecord.toCompletionMetrics(): PracticeCompletionMetrics {
    val completionRate = if (questionCount > 0) {
        (completedCount * 100) / questionCount
    } else {
        0
    }
    val settledAccuracyRate = if (completedCount > 0) {
        (correctCount * 100) / completedCount
    } else {
        null
    }
    return PracticeCompletionMetrics(
        completionRate = completionRate,
        settledAccuracyRate = settledAccuracyRate
    )
}

internal fun PracticeSessionRecord.buildSettledAccuracyText(
    resourceProvider: ResourceProvider
): String {
    return toCompletionMetrics().settledAccuracyRate?.let {
        resourceProvider.getString(R.string.practice_record_percentage, it)
    } ?: resourceProvider.getString(R.string.practice_record_not_available)
}

internal fun PracticeSessionRecord.buildSpellingSummary(
    resourceProvider: ResourceProvider
): String {
    if ((mode != PracticeMode.SPELLING && mode != PracticeMode.EXAM) || questionCount <= 0) return ""
    val metrics = toCompletionMetrics()
    return resourceProvider.getString(
        R.string.practice_record_spelling_summary,
        completedCount,
        questionCount,
        metrics.completionRate,
        correctCount,
        buildSettledAccuracyText(resourceProvider),
        submitCount
    )
}

internal fun PracticeSessionRecord.buildSpellingSummaryBrief(
    resourceProvider: ResourceProvider
): String {
    if ((mode != PracticeMode.SPELLING && mode != PracticeMode.EXAM) || questionCount <= 0) return ""
    val metrics = toCompletionMetrics()
    return resourceProvider.getString(
        R.string.practice_record_spelling_summary_brief,
        metrics.completionRate,
        buildSettledAccuracyText(resourceProvider)
    )
}
