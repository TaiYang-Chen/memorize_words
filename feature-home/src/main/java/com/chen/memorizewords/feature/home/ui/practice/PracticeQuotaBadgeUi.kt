package com.chen.memorizewords.feature.home.ui.practice

import com.chen.memorizewords.domain.practice.usage.EvaluationUsage
import com.chen.memorizewords.feature.home.R

data class PracticeQuotaBadgeUi(
    val textRes: Int,
    val remaining: Int? = null
)

internal fun buildPracticeQuotaBadgeUi(usage: EvaluationUsage?): PracticeQuotaBadgeUi {
    return when {
        usage == null -> PracticeQuotaBadgeUi(
            textRes = R.string.feature_home_shadowing_quota_badge_unknown
        )
        usage.remaining <= 0 -> PracticeQuotaBadgeUi(
            textRes = R.string.feature_home_shadowing_quota_badge_exhausted
        )
        else -> PracticeQuotaBadgeUi(
            textRes = R.string.feature_home_shadowing_quota_badge_remaining,
            remaining = usage.remaining
        )
    }
}
