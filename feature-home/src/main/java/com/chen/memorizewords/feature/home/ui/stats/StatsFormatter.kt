package com.chen.memorizewords.feature.home.ui.stats

import com.chen.memorizewords.core.common.resource.ResourceProvider
import com.chen.memorizewords.feature.home.R

internal class StatsFormatter(
    private val resourceProvider: ResourceProvider
) {

    private val weekLabels by lazy {
        listOf(
            resourceProvider.getString(R.string.home_week_mon),
            resourceProvider.getString(R.string.home_week_tue),
            resourceProvider.getString(R.string.home_week_wed),
            resourceProvider.getString(R.string.home_week_thu),
            resourceProvider.getString(R.string.home_week_fri),
            resourceProvider.getString(R.string.home_week_sat),
            resourceProvider.getString(R.string.home_week_sun)
        )
    }

    fun weekLabels(): List<String> = weekLabels

    fun formatBarDuration(durationMs: Long): String {
        val minutes = (durationMs / 60_000L).coerceAtLeast(0L)
        if (minutes <= 0L) return resourceProvider.getString(R.string.home_duration_minutes, 0)
        if (minutes < 60L) return resourceProvider.getString(R.string.home_duration_minutes, minutes)
        val hours = minutes / 60L
        return resourceProvider.getString(R.string.home_duration_hours, hours)
    }
}
