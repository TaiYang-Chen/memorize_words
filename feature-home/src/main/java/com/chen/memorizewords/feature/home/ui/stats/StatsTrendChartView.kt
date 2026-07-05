package com.chen.memorizewords.feature.home.ui.stats

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import com.chen.memorizewords.core.ui.ext.dpToPx
import kotlin.math.max

class StatsTrendChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var points: List<StatsTrendPointUi> = emptyList()
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x80EAF0F7.toInt()
        strokeWidth = 1f.dpToPx(context)
    }
    private val durationPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF2F95FF.toInt()
        strokeWidth = 1.8f.dpToPx(context)
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
        textSize = 11f.dpToPx(context)
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
        val left = 4f.dpToPx(context)
        val right = width - 4f.dpToPx(context)
        val top = 8f.dpToPx(context)
        val bottom = height - 20f.dpToPx(context)
        val chartHeight = (bottom - top).coerceAtLeast(1f.dpToPx(context))
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
            canvas.drawCircle(x, durationY, 2.6f.dpToPx(context), dotPaint)
            dotPaint.color = 0xFFFFFFFF.toInt()
            canvas.drawCircle(x, durationY, 1.2f.dpToPx(context), dotPaint)
            dotPaint.color = wordPaint.color
            canvas.drawCircle(x, wordY, 2.6f.dpToPx(context), dotPaint)
            dotPaint.color = 0xFFFFFFFF.toInt()
            canvas.drawCircle(x, wordY, 1.2f.dpToPx(context), dotPaint)
            canvas.drawText(point.dayLabel, x, height - 5f.dpToPx(context), labelPaint)
        }
        fillPath.lineTo(right, bottom)
        fillPath.close()
        canvas.drawPath(fillPath, fillPaint)
        canvas.drawPath(durationPath, durationPaint)
        canvas.drawPath(wordPath, wordPaint)
    }
}
