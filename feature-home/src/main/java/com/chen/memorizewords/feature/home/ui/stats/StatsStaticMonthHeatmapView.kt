package com.chen.memorizewords.feature.home.ui.stats

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.floor

class StatsStaticMonthHeatmapView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val cellPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val selectedStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF071436.toInt()
        style = Paint.Style.STROKE
        strokeWidth = dp(1.5f)
    }
    private val cellRect = RectF()
    private var cells: List<CalendarDayCellUi> = emptyList()
    private var onDayClick: ((CalendarDayCellUi) -> Unit)? = null

    fun submitCells(cells: List<CalendarDayCellUi>) {
        this.cells = cells.take(DAY_COUNT)
        invalidate()
    }

    fun submitStatuses(newStatuses: List<Int>) {
        cells = newStatuses
            .take(DAY_COUNT)
            .mapIndexed { index, status ->
                CalendarDayCellUi(
                    date = "",
                    dayText = (index + 1).toString(),
                    isCurrentMonth = true,
                    isToday = false,
                    isSelected = false,
                    status = status.toCalendarStudyStatus()
                )
            }
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
        val rowHeight = height / ROW_COUNT.toFloat()
        val column = floor(event.x / columnWidth).toInt()
        val row = floor(event.y / rowHeight).toInt()
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
        val rowHeight = height / ROW_COUNT.toFloat()
        val cellSize = minOf(
            dp(CELL_SIZE_DP),
            columnWidth * 0.74f,
            rowHeight * 0.96f
        ).coerceAtLeast(dp(8f))
        val radius = dp(4f)

        cells.take(DAY_COUNT).forEachIndexed { dayIndex, cell ->
            val column = dayIndex % COLUMN_COUNT
            val row = dayIndex / COLUMN_COUNT
            cellPaint.color = colorForStatus(
                if (cell.isCurrentMonth) cell.status.toHeatmapStatus() else 0
            )
            val centerX = columnWidth * column + columnWidth / 2f
            val centerY = rowHeight * row + rowHeight / 2f
            val left = centerX - cellSize / 2f
            val top = centerY - cellSize / 2f
            cellRect.set(left, top, left + cellSize, top + cellSize)
            canvas.drawRoundRect(cellRect, radius, radius, cellPaint)
            if (cell.isSelected) {
                canvas.drawRoundRect(cellRect, radius, radius, selectedStrokePaint)
            }
        }
    }

    private fun colorForStatus(status: Int): Int {
        return when (status) {
            1 -> 0xFFD8F6E1.toInt()
            2 -> 0xFF23D394.toInt()
            3 -> 0xFF069371.toInt()
            else -> 0xFFF0F3F7.toInt()
        }
    }

    private fun dp(value: Float): Float {
        return value * resources.displayMetrics.density
    }

    companion object {
        private const val COLUMN_COUNT = 7
        private const val ROW_COUNT = 6
        private const val DAY_COUNT = 42
        private const val CELL_SIZE_DP = 22f
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

private fun Int.toCalendarStudyStatus(): CalendarStudyStatus {
    return when (this) {
        1 -> CalendarStudyStatus.STUDIED
        2 -> CalendarStudyStatus.NEW_DONE
        3 -> CalendarStudyStatus.ALL_DONE
        else -> CalendarStudyStatus.NONE
    }
}
