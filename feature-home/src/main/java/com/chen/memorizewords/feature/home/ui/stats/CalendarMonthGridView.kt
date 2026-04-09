package com.chen.memorizewords.feature.home.ui.stats

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.chen.memorizewords.feature.home.R

class CalendarMonthGridView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {

    private val cellHolders = mutableListOf<DayCellHolder>()
    private val spacingPx = dp(4)

    init {
        orientation = VERTICAL
        buildGrid()
    }

    fun bind(cells: List<CalendarDayCellUi>, onDayClick: (CalendarDayCellUi) -> Unit) {
        val safeCells = if (cells.size >= CELL_COUNT) cells else cells + List(CELL_COUNT - cells.size) {
            CalendarDayCellUi(
                date = "",
                dayText = "",
                isCurrentMonth = false,
                isToday = false,
                isSelected = false,
                status = CalendarStudyStatus.NONE
            )
        }
        for (index in 0 until CELL_COUNT) {
            bindCell(cellHolders[index], safeCells[index], onDayClick)
        }
    }

    private fun buildGrid() {
        val inflater = LayoutInflater.from(context)
        repeat(ROW_COUNT) { rowIndex ->
            val row = LinearLayout(context).apply {
                orientation = HORIZONTAL
                layoutParams = LayoutParams(
                    LayoutParams.MATCH_PARENT,
                    0,
                    1f
                ).apply {
                    if (rowIndex < ROW_COUNT - 1) {
                        bottomMargin = spacingPx
                    }
                }
            }
            repeat(COLUMN_COUNT) { columnIndex ->
                val cell = inflater.inflate(R.layout.item_calendar_day, row, false)
                row.addView(
                    cell,
                    LayoutParams(
                        0,
                        LayoutParams.MATCH_PARENT,
                        1f
                    ).apply {
                        if (columnIndex < COLUMN_COUNT - 1) {
                            marginEnd = spacingPx
                        }
                    }
                )
                cellHolders.add(
                    DayCellHolder(
                        root = cell,
                        dayText = cell.findViewById(R.id.tv_calendar_day),
                        selectedView = cell.findViewById(R.id.view_day_selected),
                        todayDot = cell.findViewById(R.id.view_day_today_dot)
                    )
                )
            }
            addView(row)
        }
    }

    private fun bindCell(
        holder: DayCellHolder,
        item: CalendarDayCellUi,
        onDayClick: (CalendarDayCellUi) -> Unit
    ) {
        holder.dayText.text = item.dayText
        holder.dayText.alpha = if (item.isCurrentMonth) 1f else 0.35f
        holder.dayText.setTextColor(
            when (item.status) {
                CalendarStudyStatus.NONE -> 0xFF334155.toInt()
                CalendarStudyStatus.CHECKED_IN -> 0xFF0F172A.toInt()
                CalendarStudyStatus.STUDIED -> 0xFF0F172A.toInt()
                CalendarStudyStatus.NEW_DONE -> 0xFF0F172A.toInt()
                CalendarStudyStatus.REVIEW_DONE -> 0xFF0F172A.toInt()
                CalendarStudyStatus.ALL_DONE -> 0xFF0F172A.toInt()
            }
        )
        holder.dayText.setBackgroundResource(
            when (item.status) {
                CalendarStudyStatus.NONE -> R.drawable.feature_home_stats_day_bg_none
                CalendarStudyStatus.CHECKED_IN -> R.drawable.feature_home_stats_day_bg_checked_in
                CalendarStudyStatus.STUDIED -> R.drawable.feature_home_stats_day_bg_studied
                CalendarStudyStatus.NEW_DONE -> R.drawable.feature_home_stats_day_bg_new_done
                CalendarStudyStatus.REVIEW_DONE -> R.drawable.feature_home_stats_day_bg_review_done
                CalendarStudyStatus.ALL_DONE -> R.drawable.feature_home_stats_day_bg_all_done
            }
        )

        holder.selectedView.visibility = if (item.isSelected) View.VISIBLE else View.GONE
        holder.todayDot.visibility = if (item.isToday && !item.isSelected) View.VISIBLE else View.GONE

        holder.root.isEnabled = item.isCurrentMonth
        holder.root.alpha = if (item.isCurrentMonth) 1f else 0.7f
        if (item.isCurrentMonth) {
            holder.root.setOnClickListener {
                holder.root.animate()
                    .scaleX(0.96f)
                    .scaleY(0.96f)
                    .setDuration(70L)
                    .withEndAction {
                        holder.root.animate().scaleX(1f).scaleY(1f).setDuration(70L).start()
                    }
                    .start()
                onDayClick(item)
            }
        } else {
            holder.root.setOnClickListener(null)
        }
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    private data class DayCellHolder(
        val root: View,
        val dayText: TextView,
        val selectedView: View,
        val todayDot: View
    )

    companion object {
        private const val ROW_COUNT = 6
        private const val COLUMN_COUNT = 7
        private const val CELL_COUNT = ROW_COUNT * COLUMN_COUNT
    }
}
