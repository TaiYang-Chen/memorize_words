package com.chen.memorizewords.feature.home.ui.stats

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

class StatsStaticMonthHeatmapView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val cellPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cellRect = RectF()
    private var statuses = intArrayOf(
        0, 1, 1, 2, 1, 1, 3,
        1, 1, 1, 1, 0, 1, 0,
        1, 0, 1, 0, 1, 1, 1,
        2, 0, 1, 3, 0, 1, 1,
        2, 1, 0, 1, 0, 2, 3
    )

    fun submitCells(cells: List<CalendarDayCellUi>) {
        statuses = cells
            .filter { it.isCurrentMonth }
            .take(DAY_COUNT)
            .map { it.status.toHeatmapStatus() }
            .toIntArray()
        invalidate()
    }

    fun submitStatuses(newStatuses: List<Int>) {
        statuses = newStatuses.take(DAY_COUNT).toIntArray()
        invalidate()
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

        statuses.take(DAY_COUNT).forEachIndexed { dayIndex, status ->
            val column = dayIndex % COLUMN_COUNT
            val row = dayIndex / COLUMN_COUNT
            cellPaint.color = colorForStatus(status)
            val centerX = columnWidth * column + columnWidth / 2f
            val centerY = rowHeight * row + rowHeight / 2f
            val left = centerX - cellSize / 2f
            val top = centerY - cellSize / 2f
            cellRect.set(left, top, left + cellSize, top + cellSize)
            canvas.drawRoundRect(cellRect, radius, radius, cellPaint)
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
        private const val ROW_COUNT = 5
        private const val DAY_COUNT = 35
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
