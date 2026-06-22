package com.chen.memorizewords.feature.home.ui.stats

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import kotlin.math.max

class StatsTrendChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var points: List<StatsTrendPointUi> = emptyList()
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x80EAF0F7.toInt()
        strokeWidth = dp(1f)
    }
    private val durationPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF2F95FF.toInt()
        strokeWidth = dp(1.8f)
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val wordPaint = Paint(durationPaint).apply {
        color = 0xFF48D167.toInt()
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x183BA5F5
        style = Paint.Style.FILL
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF72819A.toInt()
        textSize = dp(11f)
        textAlign = Paint.Align.CENTER
    }

    fun submitPoints(newPoints: List<StatsTrendPointUi>) {
        points = newPoints
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val safePoints = points.ifEmpty {
            listOf("一", "二", "三", "四", "五", "六", "日").map {
                StatsTrendPointUi(it, 0f, 0)
            }
        }
        val left = dp(4f)
        val right = width - dp(4f)
        val top = dp(8f)
        val bottom = height - dp(20f)
        val chartHeight = (bottom - top).coerceAtLeast(dp(1f))
        repeat(4) { index ->
            val y = top + chartHeight * index / 3f
            canvas.drawLine(left, y, right, y, gridPaint)
        }
        val step = if (safePoints.size <= 1) 0f else (right - left) / (safePoints.size - 1)
        val maxDuration = max(1f, safePoints.maxOf { it.durationHours })
        val maxWords = max(1, safePoints.maxOf { it.newWordCount })
        val durationPath = Path()
        val wordPath = Path()
        val fillPath = Path()
        safePoints.forEachIndexed { index, point ->
            val x = left + step * index
            val durationY = bottom - (point.durationHours / maxDuration) * chartHeight
            val wordY = bottom - (point.newWordCount.toFloat() / maxWords.toFloat()) * chartHeight
            if (index == 0) {
                durationPath.moveTo(x, durationY)
                wordPath.moveTo(x, wordY)
                fillPath.moveTo(x, bottom)
                fillPath.lineTo(x, durationY)
            } else {
                durationPath.lineTo(x, durationY)
                wordPath.lineTo(x, wordY)
                fillPath.lineTo(x, durationY)
            }
            dotPaint.color = durationPaint.color
            canvas.drawCircle(x, durationY, dp(2.6f), dotPaint)
            dotPaint.color = 0xFFFFFFFF.toInt()
            canvas.drawCircle(x, durationY, dp(1.2f), dotPaint)
            dotPaint.color = wordPaint.color
            canvas.drawCircle(x, wordY, dp(2.6f), dotPaint)
            dotPaint.color = 0xFFFFFFFF.toInt()
            canvas.drawCircle(x, wordY, dp(1.2f), dotPaint)
            canvas.drawText(point.dayLabel, x, height - dp(5f), labelPaint)
        }
        fillPath.lineTo(right, bottom)
        fillPath.close()
        canvas.drawPath(fillPath, fillPaint)
        canvas.drawPath(durationPath, durationPaint)
        canvas.drawPath(wordPath, wordPaint)
    }

    private fun dp(value: Float): Float {
        return value * resources.displayMetrics.density
    }
}
