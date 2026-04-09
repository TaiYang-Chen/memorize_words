package com.chen.memorizewords.feature.home.ui.home

import android.text.Html
import android.text.Spanned
import com.chen.memorizewords.core.common.resource.ResourceProvider
import com.chen.memorizewords.domain.model.study.StudyPlan
import com.chen.memorizewords.domain.model.sync.SyncBannerState
import com.chen.memorizewords.feature.home.R

internal class HomeTextFormatter(
    private val resourceProvider: ResourceProvider
) {

    fun formatLearnButtonText(newCount: Int, plan: StudyPlan): String {
        return when {
            plan.dailyNewCount <= 0 -> resourceProvider.getString(R.string.home_learn_button_start)
            newCount <= 0 -> resourceProvider.getString(R.string.home_learn_button_start)
            newCount < plan.dailyNewCount -> resourceProvider.getString(R.string.home_learn_button_continue)
            else -> resourceProvider.getString(R.string.home_learn_button_new_more)
        }
    }

    fun formatDuration(durationMs: Long): String {
        val totalSeconds = (durationMs / 1000L).coerceAtLeast(0L)
        val hours = totalSeconds / 3600L
        val minutes = (totalSeconds % 3600L) / 60L
        return if (hours > 0L) {
            resourceProvider.getString(R.string.home_duration_hours_minutes, hours, minutes)
        } else {
            resourceProvider.getString(R.string.home_duration_minutes, minutes)
        }
    }

    fun formatLearnPlanText(
        newCount: Int,
        reviewCount: Int,
        plan: StudyPlan
    ): Spanned {
        val boundedNewCount = minOf(newCount, plan.dailyNewCount)
        val boundedReviewCount = minOf(reviewCount, plan.dailyReviewCount)
        val remainNewCount = plan.dailyNewCount - boundedNewCount
        val remainReviewCount = plan.dailyReviewCount - boundedReviewCount
        val html = when {
            remainNewCount != 0 && remainReviewCount != 0 -> resourceProvider.getString(
                R.string.home_plan_remaining_both,
                remainNewCount,
                remainReviewCount
            )

            remainNewCount == 0 && remainReviewCount != 0 -> resourceProvider.getString(
                R.string.home_plan_remaining_review_only,
                remainReviewCount
            )

            remainNewCount != 0 && remainReviewCount == 0 -> resourceProvider.getString(
                R.string.home_plan_remaining_new_only,
                remainNewCount
            )

            else -> resourceProvider.getString(R.string.home_plan_completed)
        }
        return Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY)
    }

    fun defaultLearnPlanText(): Spanned {
        return Html.fromHtml(
            resourceProvider.getString(R.string.home_plan_not_started),
            Html.FROM_HTML_MODE_LEGACY
        )
    }

    fun formatSyncBannerText(state: SyncBannerState): String {
        return when (state) {
            SyncBannerState.Hidden -> ""
            is SyncBannerState.Offline -> resourceProvider.getString(
                R.string.home_sync_banner_offline,
                state.pendingCount
            )

            is SyncBannerState.Failed -> resourceProvider.getString(
                R.string.home_sync_banner_failed,
                state.pendingCount
            )
        }
    }
}
