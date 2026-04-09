package com.chen.memorizewords.feature.home.ui.stats

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.chen.memorizewords.feature.home.R

class CalendarMonthPagerAdapter(
    private val onDayClick: (CalendarDayCellUi) -> Unit
) : RecyclerView.Adapter<CalendarMonthPagerAdapter.MonthViewHolder>() {

    private val pages = mutableListOf<CalendarMonthPageUi>()

    fun submitPages(newPages: List<CalendarMonthPageUi>) {
        pages.clear()
        pages.addAll(newPages)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MonthViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_calendar_month_page, parent, false)
        return MonthViewHolder(view, onDayClick)
    }

    override fun onBindViewHolder(holder: MonthViewHolder, position: Int) {
        holder.bind(pages[position])
    }

    override fun getItemCount(): Int = pages.size

    class MonthViewHolder(
        itemView: View,
        private val onDayClick: (CalendarDayCellUi) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {

        private val monthGrid: CalendarMonthGridView = itemView.findViewById(R.id.monthGridView)

        fun bind(page: CalendarMonthPageUi) {
            monthGrid.bind(page.cells, onDayClick)
        }
    }
}
