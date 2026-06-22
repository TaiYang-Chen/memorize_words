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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
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
                    viewModel.calendarPagerPages.collect { pages ->
                        databind.statsMonthHeatmapView.submitCells(pages.getOrNull(CURRENT_MONTH_PAGE_INDEX)?.cells.orEmpty())
                    }
                }
            }
        }
    }

    private fun renderDistributionLegend(items: List<StatsTimeDistributionUi>) {
        val safeItems = items.ifEmpty {
            listOf(
                StatsTimeDistributionUi("早晨", 25, 0xFFFFC533.toInt()),
                StatsTimeDistributionUi("上午", 25, 0xFF70D96B.toInt()),
                StatsTimeDistributionUi("下午", 25, 0xFF3BA5F5.toInt()),
                StatsTimeDistributionUi("晚上", 25, 0xFF8B5CF6.toInt())
            )
        }
        databind.llDistributionLegend.removeAllViews()
        safeItems.forEach { item ->
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
                layoutParams = LinearLayout.LayoutParams(dp(6), dp(6)).apply {
                    marginEnd = dp(7)
                }
                background = ovalDrawable(item.color)
            })
            addView(TextView(context).apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                text = item.label
                setTextColor(0xFF39465F.toInt())
                textSize = 10f
                typeface = Typeface.DEFAULT_BOLD
                includeFontPadding = false
                maxLines = 1
            })
            addView(TextView(context).apply {
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                text = "${item.percent.coerceAtLeast(0)}%"
                setTextColor(0xFF071436.toInt())
                textSize = 11f
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
                layoutParams = LinearLayout.LayoutParams(dp(34), dp(34)).apply {
                    marginEnd = dp(8)
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
                    textSize = 10f
                    typeface = Typeface.DEFAULT_BOLD
                    maxLines = 1
                })
                addView(TextView(context).apply {
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                        topMargin = dp(2)
                    }
                    text = achievement.subtitle
                    setTextColor(0xFF8190A4.toInt())
                    textSize = 8f
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
                layoutParams = LinearLayout.LayoutParams(dp(24), dp(24)).apply {
                    marginEnd = dp(9)
                }
                background = requireContext().getDrawable(row.iconBackgroundResId)
                setImageResource(row.iconResId)
                setPadding(dp(5), dp(5), dp(5), dp(5))
                contentDescription = null
            })
            addView(TextView(context).apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                text = row.label
                setTextColor(0xFF60708A.toInt())
                textSize = 10f
                typeface = Typeface.DEFAULT_BOLD
                maxLines = 1
            })
            addView(TextView(context).apply {
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                text = row.value
                setTextColor(0xFF071436.toInt())
                textSize = 16f
                typeface = Typeface.DEFAULT_BOLD
                includeFontPadding = false
                maxLines = 1
            })
            if (row.unit.isNotBlank()) {
                addView(TextView(context).apply {
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                        marginStart = dp(3)
                    }
                    text = row.unit
                    setTextColor(0xFF4F5E75.toInt())
                    textSize = 9f
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

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    private companion object {
        private const val CURRENT_MONTH_PAGE_INDEX = 1
    }
}
