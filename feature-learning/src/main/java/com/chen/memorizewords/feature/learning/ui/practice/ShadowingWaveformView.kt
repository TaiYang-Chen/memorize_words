package com.chen.memorizewords.feature.learning.ui.practice

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import com.chen.memorizewords.feature.learning.R
import kotlin.math.PI
import kotlin.math.absoluteValue
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

class ShadowingWaveformView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.feature_learning_shadowing_wave_idle)
    }
    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.feature_learning_shadowing_wave_active)
    }
    private val livePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.feature_learning_shadowing_wave_live)
    }
    private val barRect = RectF()

    private var waveformSamples: List<Int> = emptyList()
    private var progressFraction: Float = 0f
    private var liveMode: Boolean = false
    private var liveAmplitude: Float = 0.12f
    private var phase: Float = 0f

    var onSeekRequested: ((Float) -> Unit)? = null

    fun setWaveformSamples(samples: List<Int>) {
        waveformSamples = samples.map { it.coerceIn(4, 100) }
        if (!liveMode) {
            invalidate()
        }
    }

    fun setPlaybackProgress(fraction: Float) {
        progressFraction = fraction.coerceIn(0f, 1f)
        if (!liveMode) {
            invalidate()
        }
    }

    fun startLiveWave() {
        liveMode = true
        progressFraction = 0f
        invalidate()
    }

    fun stopLiveWave() {
        liveMode = false
        invalidate()
    }

    fun updateLiveAmplitude(maxAmplitude: Int) {
        val normalized = (maxAmplitude / 32767f).coerceIn(0.08f, 1f)
        liveAmplitude = normalized
        phase += 0.18f
        if (liveMode) {
            postInvalidateOnAnimation()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val availableWidth = width - paddingLeft - paddingRight
        val availableHeight = height - paddingTop - paddingBottom
        if (availableWidth <= 0 || availableHeight <= 0) return

        val barWidth = dpToPx(4f)
        val gap = dpToPx(4f)
        val step = barWidth + gap
        val barCount = max(1, (availableWidth / step).toInt())
        val centerY = paddingTop + availableHeight / 2f
        val maxBarHeight = availableHeight * 0.76f
        val radius = barWidth / 2f

        for (index in 0 until barCount) {
            val left = paddingLeft + index * step
            val normalized = if (liveMode) {
                val wave = sin(phase + index * 0.42f).absoluteValue.toFloat()
                (0.18f + wave * liveAmplitude).coerceIn(0.1f, 1f)
            } else {
                interpolateSample(index, barCount) / 100f
            }
            val barHeight = max(dpToPx(8f), normalized * maxBarHeight)
            barRect.set(left, centerY - barHeight / 2f, left + barWidth, centerY + barHeight / 2f)
            val paint = when {
                liveMode -> livePaint
                index < barCount * progressFraction -> progressPaint
                else -> barPaint
            }
            canvas.drawRoundRect(barRect, radius, radius, paint)
        }

        if (liveMode) {
            postInvalidateOnAnimation()
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (liveMode || waveformSamples.isEmpty()) return super.onTouchEvent(event)
        return when (event.actionMasked) {
            MotionEvent.ACTION_UP -> {
                val widthRange = (width - paddingLeft - paddingRight).toFloat()
                if (widthRange > 0f) {
                    val fraction = ((event.x - paddingLeft) / widthRange).coerceIn(0f, 1f)
                    onSeekRequested?.invoke(fraction)
                }
                performClick()
                true
            }

            MotionEvent.ACTION_DOWN -> true
            else -> super.onTouchEvent(event)
        }
    }

    override fun performClick(): Boolean {
        return super.performClick()
    }

    private fun interpolateSample(index: Int, count: Int): Int {
        if (waveformSamples.isEmpty()) return 18
        if (waveformSamples.size == 1 || count <= 1) return waveformSamples.first()
        val position = index / (count - 1f) * (waveformSamples.size - 1)
        val leftIndex = position.toInt()
        val rightIndex = min(waveformSamples.lastIndex, leftIndex + 1)
        val fraction = position - leftIndex
        val leftValue = waveformSamples[leftIndex]
        val rightValue = waveformSamples[rightIndex]
        return (leftValue + (rightValue - leftValue) * fraction).toInt()
    }

    private fun dpToPx(dp: Float): Float {
        return dp * resources.displayMetrics.density
    }
}
