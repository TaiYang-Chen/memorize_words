package com.chen.memorizewords.feature.home.ui.stats

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import com.chen.memorizewords.core.ui.ext.dpToPx
import java.util.Locale
import kotlin.math.ceil

class StatsTrendChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var points: List<StatsTrendPointUi> = emptyList()
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFE7EDF4.toInt()
        strokeWidth = 0.7f.dpToPx(context)
    }
    private val durationPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF12C777.toInt()
        strokeWidth = 1.6f.dpToPx(context)
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val wordPaint = Paint(durationPaint).apply {
        color = 0xFF1687E8.toInt()
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val axisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF72819A.toInt()
        textSize = 7.5f.dpToPx(context)
    }
    private val dayPaint = Paint(axisPaint).apply {
        textSize = 8f.dpToPx(context)
        textAlign = Paint.Align.CENTER
    }

    fun submitPoints(newPoints: List<StatsTrendPointUi>) {
        points = newPoints
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width <= 0 || height <= 0) return

        val safePoints = points.ifEmpty {
            listOf("一", "二", "三", "四", "五", "六", "日").map {
                StatsTrendPointUi(it, 0f, 0)
            }
        }
        val scale = calculateTrendScale(safePoints)
        val chartLeft = 23f.dpToPx(context)
        val chartRight = width - 23f.dpToPx(context)
        val chartTop = 3f.dpToPx(context)
        val chartBottom = height - 14f.dpToPx(context)
        val chartHeight = (chartBottom - chartTop).coerceAtLeast(1f.dpToPx(context))

        repeat(3) { index ->
            val fraction = index / 2f
            val y = chartTop + chartHeight * fraction
            canvas.drawLine(chartLeft, y, chartRight, y, gridPaint)

            val durationValue = scale.durationMaxHours * (1f - fraction)
            axisPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText(
                formatDurationAxis(durationValue),
                chartLeft - 5f.dpToPx(context),
                axisBaseline(y, axisPaint),
                axisPaint
            )

            val wordValue = (scale.wordMaxCount * (1f - fraction)).toInt()
            axisPaint.textAlign = Paint.Align.LEFT
            canvas.drawText(
                wordValue.toString(),
                chartRight + 5f.dpToPx(context),
                axisBaseline(y, axisPaint),
                axisPaint
            )
        }

        val step = if (safePoints.size <= 1) 0f else {
            (chartRight - chartLeft) / (safePoints.size - 1)
        }
        val durationPath = Path()
        val wordPath = Path()
        safePoints.forEachIndexed { index, point ->
            val x = chartLeft + step * index
            val durationY = chartBottom -
                (point.durationHours.coerceAtLeast(0f) / scale.durationMaxHours) * chartHeight
            val wordY = chartBottom -
                (point.newWordCount.coerceAtLeast(0).toFloat() / scale.wordMaxCount) * chartHeight

            if (index == 0) {
                durationPath.moveTo(x, durationY)
                wordPath.moveTo(x, wordY)
            } else {
                durationPath.lineTo(x, durationY)
                wordPath.lineTo(x, wordY)
            }
            drawPoint(canvas, x, durationY, durationPaint.color)
            drawPoint(canvas, x, wordY, wordPaint.color)
            canvas.drawText(point.dayLabel, x, height - 2.5f.dpToPx(context), dayPaint)
        }
        canvas.drawPath(durationPath, durationPaint)
        canvas.drawPath(wordPath, wordPaint)
    }

    private fun drawPoint(canvas: Canvas, x: Float, y: Float, color: Int) {
        dotPaint.color = color
        canvas.drawCircle(x, y, 2.1f.dpToPx(context), dotPaint)
        dotPaint.color = 0xFFFFFFFF.toInt()
        canvas.drawCircle(x, y, 0.85f.dpToPx(context), dotPaint)
    }

    private fun axisBaseline(centerY: Float, paint: Paint): Float {
        return centerY - (paint.descent() + paint.ascent()) / 2f
    }
}

internal data class StatsTrendScale(
    val durationMaxHours: Float,
    val wordMaxCount: Float
)

internal fun calculateTrendScale(points: List<StatsTrendPointUi>): StatsTrendScale {
    val durationMax = ceil(points.maxOfOrNull { it.durationHours.coerceAtLeast(0f) }?.toDouble() ?: 0.0)
        .toFloat()
        .coerceAtLeast(MIN_DURATION_AXIS_HOURS)
    val rawWordMax = points.maxOfOrNull { it.newWordCount.coerceAtLeast(0) } ?: 0
    val wordMax = (ceil(rawWordMax / WORD_AXIS_STEP.toDouble()) * WORD_AXIS_STEP)
        .toFloat()
        .coerceAtLeast(MIN_WORD_AXIS_COUNT)
    return StatsTrendScale(durationMax, wordMax)
}

private fun formatDurationAxis(value: Float): String {
    return if (value == 0f) "0" else String.format(Locale.US, "%.1f", value)
}

private const val MIN_DURATION_AXIS_HOURS = 2f
private const val MIN_WORD_AXIS_COUNT = 20f
private const val WORD_AXIS_STEP = 10
