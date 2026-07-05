package com.chen.memorizewords.feature.home.ui.stats

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import com.chen.memorizewords.core.ui.ext.dpToPx

class StatsDonutChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var items: List<StatsTimeDistributionUi> = emptyList()
    private val segmentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.BUTT
    }
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFEFF4FA.toInt()
        style = Paint.Style.STROKE
    }

    fun submitItems(newItems: List<StatsTimeDistributionUi>) {
        items = newItems
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val stroke = 15f.dpToPx(context)
        segmentPaint.strokeWidth = stroke
        trackPaint.strokeWidth = stroke
        val padding = stroke / 2f + 3f.dpToPx(context)
        val size = (width.coerceAtMost(height)).toFloat() - padding * 2f
        val left = (width - size) / 2f
        val top = (height - size) / 2f
        val oval = RectF(left, top, left + size, top + size)
        canvas.drawArc(oval, -90f, 360f, false, trackPaint)
        val safeItems = items.ifEmpty {
            listOf(
                StatsTimeDistributionUi("", 25, 0xFFFFC533.toInt()),
                StatsTimeDistributionUi("", 25, 0xFF70D96B.toInt()),
                StatsTimeDistributionUi("", 25, 0xFF3BA5F5.toInt()),
                StatsTimeDistributionUi("", 25, 0xFF8B5CF6.toInt())
            )
        }
        var start = -90f
        safeItems.forEach { item ->
            val sweep = item.percent.coerceAtLeast(0) * 3.6f
            segmentPaint.color = item.color
            canvas.drawArc(oval, start, sweep, false, segmentPaint)
            start += sweep
        }
    }
}
