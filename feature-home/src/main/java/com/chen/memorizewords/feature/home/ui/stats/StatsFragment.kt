package com.chen.memorizewords.feature.home.ui.stats

import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.viewpager2.widget.ViewPager2
import com.chen.memorizewords.core.ui.fragment.BaseFragment
import com.chen.memorizewords.feature.home.R
import com.chen.memorizewords.feature.home.databinding.ModuleHomeFragmentStatsBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlin.math.abs
import kotlinx.coroutines.launch

@AndroidEntryPoint
class StatsFragment : BaseFragment<StatsViewModel, ModuleHomeFragmentStatsBinding>() {

    override val viewModel: StatsViewModel by lazy {
        ViewModelProvider(this)[StatsViewModel::class.java]
    }

    private val monthPagerAdapter by lazy {
        CalendarMonthPagerAdapter { day ->
            onCalendarDayClicked(day)
        }
    }
    private var canGoNextMonth: Boolean = false
    private var pagerReady: Boolean = false
    private var selectedPagePosition: Int = 1
    private val pagerCallback = object : ViewPager2.OnPageChangeCallback() {
        override fun onPageSelected(position: Int) {
            selectedPagePosition = position
        }

        override fun onPageScrollStateChanged(state: Int) {
            if (!pagerReady) return
            if (state != ViewPager2.SCROLL_STATE_IDLE) return
            when (selectedPagePosition) {
                0 -> {
                    performLightHaptic()
                    viewModel.shiftToPreviousMonth()
                    resetPagerToCenter()
                }

                2 -> {
                    val moved = viewModel.shiftToNextMonth()
                    if (moved) {
                        performLightHaptic()
                    } else {
                        showMonthBoundaryFeedback()
                    }
                    resetPagerToCenter()
                }
            }
        }
    }

    override fun initView(savedInstanceState: Bundle?) {
        databind.viewModel = viewModel
        databind.lifecycleOwner = viewLifecycleOwner
        setupCalendarPager()
        setupMonthActions()
    }

    override fun createObserver() {
        lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.monthTitle.collect { databind.tvMonthValue.text = it }
                }
                launch {
                    viewModel.calendarPagerPages.collect {
                        monthPagerAdapter.submitPages(it)
                        if (!pagerReady && it.size > 1) {
                            pagerReady = true
                            selectedPagePosition = 1
                            databind.vpCalendar.setCurrentItem(1, false)
                        } else if (pagerReady && it.size > 1 && databind.vpCalendar.currentItem != 1) {
                            databind.vpCalendar.setCurrentItem(1, false)
                        }
                    }
                }
                launch {
                    viewModel.canGoNextMonth.collect {
                        canGoNextMonth = it
                        databind.btnNextMonth.isEnabled = it
                        databind.btnNextMonth.alpha = if (it) 1f else 0.45f
                    }
                }
                launch {
                    viewModel.showBackToday.collect {
                        databind.btnBackToday.visibility = if (it) View.VISIBLE else View.GONE
                    }
                }
                launch {
                    viewModel.totalStudyDaysText.collect {
                        databind.tvAchievementDays.text = it
                        databind.tvDescription.text = "你已与词相伴 $it 余日，语言在你身上，缓慢生长。"
                    }
                }
                launch {
                    viewModel.totalStudyDurationText.collect {
                        databind.tvAchievementDuration.text = it
                    }
                }
                launch {
                    viewModel.totalStudyWordsText.collect { databind.tvAchievementWords.text = it }
                }
                launch {
                    viewModel.weekWordBars.collect {
                        renderBars(
                            databind.llWeekWordBars,
                            it,
                            R.drawable.feature_home_stats_bar_word
                        )
                    }
                }
                launch {
                    viewModel.weekDurationBars.collect {
                        renderBars(
                            databind.llWeekDurationBars,
                            it,
                            R.drawable.feature_home_stats_bar_duration
                        )
                    }
                }
                launch {
                    viewModel.wordFilter.collect { filter ->
                        databind.btnFilterAll.isSelected = filter == WeeklyWordFilter.ALL
                        databind.btnFilterNew.isSelected = filter == WeeklyWordFilter.NEW
                        databind.btnFilterReview.isSelected = filter == WeeklyWordFilter.REVIEW
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        databind.vpCalendar.unregisterOnPageChangeCallback(pagerCallback)
        super.onDestroyView()
    }

    private fun setupCalendarPager() {
        databind.vpCalendar.apply {
            adapter = monthPagerAdapter
            offscreenPageLimit = 1
            registerOnPageChangeCallback(pagerCallback)
            setPageTransformer { page, position ->
                page.alpha = 0.85f + (1f - abs(position)) * 0.15f
            }
        }
    }

    private fun setupMonthActions() {
        databind.btnPrevMonth.setOnClickListener {
            if (!pagerReady) return@setOnClickListener
            performLightHaptic()
            databind.vpCalendar.setCurrentItem(0, true)
        }
        databind.btnNextMonth.setOnClickListener {
            if (!pagerReady) return@setOnClickListener
            if (canGoNextMonth) {
                performLightHaptic()
                databind.vpCalendar.setCurrentItem(2, true)
            } else {
                showMonthBoundaryFeedback()
            }
        }
    }

    private fun resetPagerToCenter() {
        selectedPagePosition = 1
        if (databind.vpCalendar.currentItem != 1) {
            databind.vpCalendar.setCurrentItem(1, false)
        }
    }

    private fun onCalendarDayClicked(day: CalendarDayCellUi) {
        if (!day.isCurrentMonth) return
        performLightHaptic()
        viewModel.selectDate(day.date)
        showDayDetailSheet(day.date)
    }

    private fun showDayDetailSheet(date: String) {
        val existing = childFragmentManager.findFragmentByTag(StatsDayDetailBottomSheetDialog.TAG)
        if (existing != null) return
        StatsDayDetailBottomSheetDialog.newInstance(date)
            .show(childFragmentManager, StatsDayDetailBottomSheetDialog.TAG)
    }

    private fun showMonthBoundaryFeedback() {
        performRejectHaptic()
        viewModel.showToast(getString(R.string.home_calendar_future_disabled))
    }

    private fun performLightHaptic() {
        databind.root.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    private fun performRejectHaptic() {
        databind.root.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    }

    private fun renderBars(container: LinearLayout, bars: List<WeekBarUi>, barResId: Int) {
        container.removeAllViews()
        val inflater = LayoutInflater.from(container.context)
        val maxValue = bars.maxOfOrNull { it.value } ?: 0L
        val maxBarHeight = dp(110)
        val minBarHeight = dp(6)

        bars.forEach { bar ->
            val itemView = inflater.inflate(R.layout.item_week_bar, container, false)
            val tvValue = itemView.findViewById<TextView>(R.id.tv_bar_value)
            val tvDay = itemView.findViewById<TextView>(R.id.tv_bar_day)
            val barView = itemView.findViewById<View>(R.id.view_bar)

            tvValue.text = bar.valueLabel
            tvDay.text = bar.dayLabel
            barView.setBackgroundResource(barResId)

            val calculatedHeight = if (maxValue <= 0L) {
                minBarHeight
            } else {
                ((bar.value.toDouble() / maxValue.toDouble()) * maxBarHeight).toInt()
                    .coerceAtLeast(minBarHeight)
            }
            barView.layoutParams = barView.layoutParams.apply {
                height = calculatedHeight
            }

            container.addView(itemView)
        }
    }

    private fun dp(value: Int): Int {
        val density = resources.displayMetrics.density
        return (value * density).toInt()
    }
}
