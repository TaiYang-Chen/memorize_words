package com.chen.memorizewords.feature.home.ui.stats

import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.chen.memorizewords.core.ui.R as CoreUiR
import com.chen.memorizewords.core.ui.ext.dimenPx
import com.chen.memorizewords.core.ui.ext.setTextSizeFromDimen
import com.chen.memorizewords.core.ui.fragment.BaseFragment
import com.chen.memorizewords.feature.home.R
import com.chen.memorizewords.feature.home.databinding.ModuleHomeFragmentStatsBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class StatsFragment : BaseFragment<StatsViewModel, ModuleHomeFragmentStatsBinding>() {

    override val viewModel: StatsViewModel by lazy {
        ViewModelProvider(this)[StatsViewModel::class.java]
    }

    override fun initView(savedInstanceState: Bundle?) {
        databind.viewModel = viewModel
        databind.lifecycleOwner = viewLifecycleOwner
        databind.statsPreviousMonthButton.setOnClickListener {
            viewModel.shiftToPreviousMonth()
        }
        databind.statsNextMonthButton.setOnClickListener {
            if (!viewModel.shiftToNextMonth()) {
                Toast.makeText(
                    requireContext(),
                    getString(R.string.home_calendar_future_disabled),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
        databind.statsBackToday.setOnClickListener {
            viewModel.backToToday()
        }
        databind.statsMonthHeatmapView.setOnDayClickListener { day ->
            viewModel.selectDate(day.date)
            showDayDetail(day.date)
        }
    }

    override fun createObserver() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.trendPoints.collect { points ->
                        databind.trendChartView.submitPoints(points)
                    }
                }
                launch {
                    viewModel.timeDistribution.collect { distribution ->
                        databind.donutChartView.submitItems(distribution)
                        renderDistributionLegend(distribution)
                    }
                }
                launch {
                    viewModel.achievements.collect(::renderAchievements)
                }
                launch {
                    viewModel.reportRows.collect(::renderReportRows)
                }
                launch {
                    viewModel.overviewCards.collect(::renderOverviewCards)
                }
                launch {
                    viewModel.monthTitle.collect { title ->
                        databind.statsMonthTitle.text = title
                    }
                }
                launch {
                    viewModel.canGoNextMonth.collect { canGoNext ->
                        databind.statsNextMonthButton.isEnabled = canGoNext
                        databind.statsNextMonthButton.alpha = if (canGoNext) 1f else 0.35f
                    }
                }
                launch {
                    viewModel.showBackToday.collect { showBackToday ->
                        databind.statsBackToday.visibility = if (showBackToday) {
                            View.VISIBLE
                        } else {
                            View.GONE
                        }
                    }
                }
                launch {
                    viewModel.calendarPagerPages.collect { pages ->
                        databind.statsMonthHeatmapView.submitCells(pages.getOrNull(CURRENT_MONTH_PAGE_INDEX)?.cells.orEmpty())
                    }
                }
            }
        }
    }

    private fun showDayDetail(date: String) {
        val fragmentManager = parentFragmentManager
        if (fragmentManager.findFragmentByTag(StatsDayDetailBottomSheetDialog.TAG) != null) {
            return
        }
        StatsDayDetailBottomSheetDialog
            .newInstance(date)
            .show(fragmentManager, StatsDayDetailBottomSheetDialog.TAG)
    }

    private fun renderDistributionLegend(items: List<StatsTimeDistributionUi>) {
        databind.llDistributionLegend.removeAllViews()
        items.forEach { item ->
            databind.llDistributionLegend.addView(createDistributionLegendRow(item))
        }
    }

    private fun createDistributionLegendRow(item: StatsTimeDistributionUi): View {
        return LinearLayout(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
            gravity = Gravity.CENTER_VERTICAL
            orientation = LinearLayout.HORIZONTAL

            addView(View(context).apply {
                layoutParams = LinearLayout.LayoutParams(dimen(CoreUiR.dimen.core_ui_dp_6), dimen(CoreUiR.dimen.core_ui_dp_6)).apply {
                    marginEnd = dimen(CoreUiR.dimen.core_ui_dp_7)
                }
                background = ovalDrawable(item.color)
            })
            addView(TextView(context).apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                text = item.label
                setTextColor(0xFF39465F.toInt())
                setTextSizeFromDimen(CoreUiR.dimen.core_ui_sp_10)
                typeface = Typeface.DEFAULT_BOLD
                includeFontPadding = false
                maxLines = 1
            })
            addView(TextView(context).apply {
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                text = "${item.percent.coerceAtLeast(0)}%"
                setTextColor(0xFF071436.toInt())
                setTextSizeFromDimen(CoreUiR.dimen.core_ui_sp_11)
                typeface = Typeface.DEFAULT_BOLD
                includeFontPadding = false
            })
        }
    }

    private fun renderAchievements(achievements: List<StatsAchievementUi>) {
        databind.llAchievements.removeAllViews()
        achievements.take(4).forEach { achievement ->
            databind.llAchievements.addView(createAchievementView(achievement))
        }
    }

    private fun createAchievementView(achievement: StatsAchievementUi): View {
        return LinearLayout(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
            gravity = Gravity.CENTER_VERTICAL
            orientation = LinearLayout.HORIZONTAL

            addView(ImageView(context).apply {
                layoutParams = LinearLayout.LayoutParams(dimen(CoreUiR.dimen.core_ui_dp_34), dimen(CoreUiR.dimen.core_ui_dp_34)).apply {
                    marginEnd = dimen(CoreUiR.dimen.core_ui_dp_8)
                }
                setImageResource(if (achievement.achieved) achievement.iconResId else R.drawable.feature_home_ic_achievement_locked)
                alpha = if (achievement.achieved) 1f else 0.9f
                contentDescription = null
            })
            addView(LinearLayout(context).apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                orientation = LinearLayout.VERTICAL

                addView(TextView(context).apply {
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    text = achievement.title
                    setTextColor(0xFF071436.toInt())
                    setTextSizeFromDimen(CoreUiR.dimen.core_ui_sp_10)
                    typeface = Typeface.DEFAULT_BOLD
                    maxLines = 1
                })
                addView(TextView(context).apply {
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                        topMargin = dimen(CoreUiR.dimen.core_ui_dp_2)
                    }
                    text = achievement.subtitle
                    setTextColor(0xFF8190A4.toInt())
                    setTextSizeFromDimen(CoreUiR.dimen.core_ui_sp_8)
                    maxLines = 1
                })
            })
        }
    }

    private fun renderReportRows(rows: List<StatsReportRowUi>) {
        databind.llReportRows.removeAllViews()
        rows.take(4).forEach { row ->
            databind.llReportRows.addView(createReportRow(row))
        }
    }

    private fun renderOverviewCards(cards: List<StatsOverviewCardUi>) {
        bindOverviewCard(
            card = cards.getOrNull(0),
            valueView = databind.statsOverviewWordsValue,
            unitView = databind.statsOverviewWordsUnit,
            titleView = databind.statsOverviewWordsTitle,
            changeView = databind.statsOverviewWordsChange
        )
        bindOverviewCard(
            card = cards.getOrNull(1),
            valueView = databind.statsOverviewStreakValue,
            unitView = databind.statsOverviewStreakUnit,
            titleView = databind.statsOverviewStreakTitle,
            changeView = databind.statsOverviewStreakChange
        )
        bindOverviewCard(
            card = cards.getOrNull(2),
            valueView = databind.statsOverviewDurationValue,
            unitView = databind.statsOverviewDurationUnit,
            titleView = databind.statsOverviewDurationTitle,
            changeView = databind.statsOverviewDurationChange
        )
        bindOverviewCard(
            card = cards.getOrNull(3),
            valueView = databind.statsOverviewAccuracyValue,
            unitView = databind.statsOverviewAccuracyUnit,
            titleView = databind.statsOverviewAccuracyTitle,
            changeView = databind.statsOverviewAccuracyChange
        )
    }

    private fun bindOverviewCard(
        card: StatsOverviewCardUi?,
        valueView: TextView,
        unitView: TextView,
        titleView: TextView,
        changeView: TextView
    ) {
        val safeCard = card ?: return
        valueView.text = safeCard.value
        unitView.text = safeCard.unit
        titleView.text = safeCard.title
        changeView.text = safeCard.changeText
    }

    private fun createReportRow(row: StatsReportRowUi): View {
        return LinearLayout(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
            gravity = Gravity.CENTER_VERTICAL
            orientation = LinearLayout.HORIZONTAL

            addView(ImageView(context).apply {
                layoutParams = LinearLayout.LayoutParams(dimen(CoreUiR.dimen.core_ui_dp_24), dimen(CoreUiR.dimen.core_ui_dp_24)).apply {
                    marginEnd = dimen(CoreUiR.dimen.core_ui_dp_9)
                }
                background = requireContext().getDrawable(row.iconBackgroundResId)
                setImageResource(row.iconResId)
                setPadding(dimen(CoreUiR.dimen.core_ui_dp_5), dimen(CoreUiR.dimen.core_ui_dp_5), dimen(CoreUiR.dimen.core_ui_dp_5), dimen(CoreUiR.dimen.core_ui_dp_5))
                contentDescription = null
            })
            addView(TextView(context).apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                text = row.label
                setTextColor(0xFF60708A.toInt())
                setTextSizeFromDimen(CoreUiR.dimen.core_ui_sp_10)
                typeface = Typeface.DEFAULT_BOLD
                maxLines = 1
            })
            addView(TextView(context).apply {
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                text = row.value
                setTextColor(0xFF071436.toInt())
                setTextSizeFromDimen(CoreUiR.dimen.core_ui_text_title)
                typeface = Typeface.DEFAULT_BOLD
                includeFontPadding = false
                maxLines = 1
            })
            if (row.unit.isNotBlank()) {
                addView(TextView(context).apply {
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                        marginStart = dimen(CoreUiR.dimen.core_ui_dp_3)
                    }
                    text = row.unit
                    setTextColor(0xFF4F5E75.toInt())
                    setTextSizeFromDimen(CoreUiR.dimen.core_ui_sp_9)
                    typeface = Typeface.DEFAULT_BOLD
                    maxLines = 1
                })
            }
        }
    }

    private fun ovalDrawable(color: Int): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
        }
    }
    private fun dimen(id: Int): Int {
        return requireContext().dimenPx(id)
    }

    private companion object {
        private const val CURRENT_MONTH_PAGE_INDEX = 1
    }
}
