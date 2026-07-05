package com.chen.memorizewords.feature.learning.ui.practice

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import com.chen.memorizewords.core.ui.ext.dpToPx
import com.chen.memorizewords.feature.learning.R
import kotlin.math.max
import kotlin.math.min

data class WaveformPeak(
    val min: Float,
    val max: Float,
    val rms: Float
)

class ShadowingWaveformView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val wavePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.feature_learning_shadowing_wave_idle)
        style = Paint.Style.FILL
    }
    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.feature_learning_shadowing_wave_active)
        style = Paint.Style.FILL
    }
    private val livePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.feature_learning_shadowing_wave_live)
        style = Paint.Style.FILL
    }
    private val rmsPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.feature_learning_shadowing_wave_rms)
        style = Paint.Style.FILL
    }
    private val centerLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.feature_learning_shadowing_wave_center)
        strokeWidth = dpToPx(1f)
    }
    private val peakPath = Path()
    private val rmsPath = Path()

    private var waveformSamples: List<Int> = emptyList()
    private var waveformPeaks: List<WaveformPeak> = emptyList()
    private val livePeaks = ArrayDeque<WaveformPeak>()
    private var progressFraction: Float = 0f
    private var liveMode: Boolean = false

    var onSeekRequested: ((Float) -> Unit)? = null

    fun setWaveformSamples(samples: List<Int>) {
        waveformSamples = samples.map { it.coerceIn(0, 100) }
        waveformPeaks = waveformSamples.map { value ->
            val normalized = value / 100f
            WaveformPeak(min = -normalized, max = normalized, rms = normalized * 0.68f)
        }
        if (!liveMode) {
            invalidate()
        }
    }

    fun setWaveformPeaks(peaks: List<WaveformPeak>) {
        waveformSamples = emptyList()
        waveformPeaks = peaks.map { peak ->
            WaveformPeak(
                min = peak.min.coerceIn(-1f, 0f),
                max = peak.max.coerceIn(0f, 1f),
                rms = peak.rms.coerceIn(0f, 1f)
            )
        }
        if (!liveMode) {
            invalidate()
        }
    }

    fun clearWaveform() {
        waveformSamples = emptyList()
        waveformPeaks = emptyList()
        progressFraction = 0f
        invalidate()
    }

    fun setPlaybackProgress(fraction: Float) {
        progressFraction = fraction.coerceIn(0f, 1f)
        if (!liveMode) {
            invalidate()
        }
    }

    fun startLiveWave() {
        liveMode = true
        livePeaks.clear()
        repeat(LIVE_PEAK_CAPACITY) {
            livePeaks.addLast(WaveformPeak(min = -0.02f, max = 0.02f, rms = 0.015f))
        }
        progressFraction = 0f
        invalidate()
    }

    fun stopLiveWave() {
        liveMode = false
        invalidate()
    }

    fun updateLiveAmplitude(maxAmplitude: Int) {
        val peak = (maxAmplitude / 32767f).coerceIn(0f, 1f)
        val visiblePeak = max(peak, 0.015f)
        while (livePeaks.size >= LIVE_PEAK_CAPACITY) {
            livePeaks.removeFirst()
        }
        livePeaks.addLast(
            WaveformPeak(
                min = -visiblePeak,
                max = visiblePeak,
                rms = visiblePeak * 0.68f
            )
        )
        if (liveMode) {
            postInvalidateOnAnimation()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val availableWidth = width - paddingLeft - paddingRight
        val availableHeight = height - paddingTop - paddingBottom
        if (availableWidth <= 0 || availableHeight <= 0) return

        val centerY = paddingTop + availableHeight / 2f
        val amplitudeHeight = availableHeight * 0.46f
        canvas.drawLine(
            paddingLeft.toFloat(),
            centerY,
            (paddingLeft + availableWidth).toFloat(),
            centerY,
            centerLinePaint
        )

        val peaks = displayPeaks()
        if (peaks.isEmpty()) return

        if (liveMode) {
            drawPeakRange(canvas, peaks, 0, peaks.size, livePaint, centerY, amplitudeHeight)
        } else {
            val progressIndex = (peaks.size * progressFraction).toInt().coerceIn(0, peaks.size)
            drawPeakRange(canvas, peaks, progressIndex, peaks.size, wavePaint, centerY, amplitudeHeight)
            drawPeakRange(canvas, peaks, 0, progressIndex, progressPaint, centerY, amplitudeHeight)
        }

        drawRmsRange(canvas, peaks, 0, peaks.size, centerY, amplitudeHeight)

        if (liveMode) {
            postInvalidateOnAnimation()
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (liveMode || displayPeaks().isEmpty()) return super.onTouchEvent(event)
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

    private fun displayPeaks(): List<WaveformPeak> {
        return if (liveMode) livePeaks.toList() else waveformPeaks
    }

    private fun drawPeakRange(
        canvas: Canvas,
        peaks: List<WaveformPeak>,
        startIndex: Int,
        endIndex: Int,
        paint: Paint,
        centerY: Float,
        amplitudeHeight: Float
    ) {
        if (endIndex <= startIndex || peaks.isEmpty()) return
        val stepX = xStep(peaks)
        peakPath.reset()
        peakPath.moveTo(xForIndex(startIndex, stepX), centerY)
        for (index in startIndex until endIndex) {
            val peak = peaks[index]
            peakPath.lineTo(xForIndex(index, stepX), centerY - peak.max * amplitudeHeight)
        }
        for (index in endIndex - 1 downTo startIndex) {
            val peak = peaks[index]
            peakPath.lineTo(xForIndex(index, stepX), centerY - peak.min * amplitudeHeight)
        }
        peakPath.close()
        canvas.drawPath(peakPath, paint)
    }

    private fun drawRmsRange(
        canvas: Canvas,
        peaks: List<WaveformPeak>,
        startIndex: Int,
        endIndex: Int,
        centerY: Float,
        amplitudeHeight: Float
    ) {
        if (endIndex <= startIndex || peaks.isEmpty()) return
        val stepX = xStep(peaks)
        rmsPath.reset()
        rmsPath.moveTo(xForIndex(startIndex, stepX), centerY)
        for (index in startIndex until endIndex) {
            val peak = peaks[index]
            rmsPath.lineTo(xForIndex(index, stepX), centerY - peak.rms * amplitudeHeight)
        }
        for (index in endIndex - 1 downTo startIndex) {
            val peak = peaks[index]
            rmsPath.lineTo(xForIndex(index, stepX), centerY + peak.rms * amplitudeHeight)
        }
        rmsPath.close()
        canvas.drawPath(rmsPath, rmsPaint)
    }

    private fun xStep(peaks: List<WaveformPeak>): Float {
        val availableWidth = width - paddingLeft - paddingRight
        return if (peaks.size <= 1) 0f else availableWidth / (peaks.size - 1f)
    }

    private fun xForIndex(index: Int, stepX: Float): Float {
        return paddingLeft + index * stepX
    }

    private fun dpToPx(dp: Float): Float {
        return dp.dpToPx(context)
    }

    companion object {
        private const val LIVE_PEAK_CAPACITY = 96
    }
}
