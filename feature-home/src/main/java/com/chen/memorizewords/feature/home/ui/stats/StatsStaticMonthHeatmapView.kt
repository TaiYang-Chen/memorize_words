package com.chen.memorizewords.feature.home.ui.stats

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.chen.memorizewords.core.ui.ext.dpToPx
import kotlin.math.ceil
import kotlin.math.floor

class StatsStaticMonthHeatmapView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val cellPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val selectedStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF071436.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 1.5f.dpToPx(context)
    }
    private val dayTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
        textSize = 8f.dpToPx(context)
    }
    private val cellRect = RectF()
    private var cells: List<CalendarDayCellUi> = emptyList()
    private var visibleRowCount: Int = ROW_COUNT
    private var onDayClick: ((CalendarDayCellUi) -> Unit)? = null

    fun submitCells(cells: List<CalendarDayCellUi>) {
        this.cells = cells.take(DAY_COUNT)
        visibleRowCount = calculateVisibleRowCount(this.cells)
        invalidate()
    }

    fun setOnDayClickListener(listener: ((CalendarDayCellUi) -> Unit)?) {
        onDayClick = listener
        isClickable = listener != null
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (onDayClick == null || event.action != MotionEvent.ACTION_UP) {
            return super.onTouchEvent(event)
        }
        val columnWidth = width / COLUMN_COUNT.toFloat()
        val rowHeight = height / visibleRowCount.toFloat()
        val column = floor(event.x / columnWidth).toInt()
        val row = floor(event.y / rowHeight).toInt()
        if (row !in 0 until visibleRowCount) {
            return super.onTouchEvent(event)
        }
        val index = row * COLUMN_COUNT + column
        val cell = cells.getOrNull(index)
        return if (cell != null && cell.isCurrentMonth && cell.date.isNotBlank()) {
            performClick()
            onDayClick?.invoke(cell)
            true
        } else {
            super.onTouchEvent(event)
        }
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val columnWidth = width / COLUMN_COUNT.toFloat()
        val rowHeight = height / visibleRowCount.toFloat()
        val cellWidth = minOf(
            CELL_WIDTH_DP.dpToPx(context),
            columnWidth * 0.72f
        ).coerceAtLeast(16f.dpToPx(context))
        val cellHeight = minOf(
            CELL_HEIGHT_DP.dpToPx(context),
            rowHeight * 0.84f
        ).coerceAtLeast(12f.dpToPx(context))
        val radius = 3.5f.dpToPx(context)
        val visibleCellCount = visibleRowCount * COLUMN_COUNT

        cells.take(visibleCellCount).forEachIndexed { dayIndex, cell ->
            val column = dayIndex % COLUMN_COUNT
            val row = dayIndex / COLUMN_COUNT
            cellPaint.color = colorForStatus(
                if (cell.isCurrentMonth) cell.status.toHeatmapStatus() else 0
            )
            val centerX = columnWidth * column + columnWidth / 2f
            val centerY = rowHeight * row + rowHeight / 2f
            val left = centerX - cellWidth / 2f
            val top = centerY - cellHeight / 2f
            cellRect.set(left, top, left + cellWidth, top + cellHeight)
            canvas.drawRoundRect(cellRect, radius, radius, cellPaint)
            if (cell.isSelected) {
                canvas.drawRoundRect(cellRect, radius, radius, selectedStrokePaint)
            }
            val dayLabel = heatmapDayLabel(cell)
            if (dayLabel.isNotEmpty()) {
                dayTextPaint.color = textColorForStatus(cell.status)
                val baseline = centerY - (dayTextPaint.descent() + dayTextPaint.ascent()) / 2f
                canvas.drawText(dayLabel, centerX, baseline, dayTextPaint)
            }
        }
    }

    private fun calculateVisibleRowCount(cells: List<CalendarDayCellUi>): Int {
        val lastCurrentMonthIndex = cells.indexOfLast { it.isCurrentMonth }
        if (lastCurrentMonthIndex == -1) return ROW_COUNT
        return ceil((lastCurrentMonthIndex + 1) / COLUMN_COUNT.toFloat())
            .toInt()
            .coerceIn(1, ROW_COUNT)
    }

    private fun colorForStatus(status: Int): Int {
        return when (status) {
            1 -> 0xFFD8F6E1.toInt()
            2 -> 0xFF23D394.toInt()
            3 -> 0xFF069371.toInt()
            else -> 0xFFEEF2F7.toInt()
        }
    }

    private fun textColorForStatus(status: CalendarStudyStatus): Int {
        return when (status) {
            CalendarStudyStatus.NEW_DONE,
            CalendarStudyStatus.REVIEW_DONE,
            CalendarStudyStatus.ALL_DONE -> 0xFFFFFFFF.toInt()
            CalendarStudyStatus.NONE,
            CalendarStudyStatus.CHECKED_IN,
            CalendarStudyStatus.STUDIED -> 0xFF60708A.toInt()
        }
    }

    companion object {
        private const val COLUMN_COUNT = 7
        private const val ROW_COUNT = 6
        private const val DAY_COUNT = 42
        private const val CELL_WIDTH_DP = 22f
        private const val CELL_HEIGHT_DP = 16f
    }
}

internal fun heatmapDayLabel(cell: CalendarDayCellUi): String {
    return when {
        !cell.isCurrentMonth -> ""
        cell.isToday -> "今"
        else -> cell.dayText
    }
}

private fun CalendarStudyStatus.toHeatmapStatus(): Int {
    return when (this) {
        CalendarStudyStatus.CHECKED_IN,
        CalendarStudyStatus.STUDIED -> 1
        CalendarStudyStatus.NEW_DONE,
        CalendarStudyStatus.REVIEW_DONE -> 2
        CalendarStudyStatus.ALL_DONE -> 3
        CalendarStudyStatus.NONE -> 0
    }
}
