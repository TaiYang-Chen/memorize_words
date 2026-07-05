package com.chen.memorizewords.feature.learning.ui.practice

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.chen.memorizewords.core.ui.ext.dpToPx
import kotlin.math.hypot

class HandwritingCanvasView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#EEF2FF")
        style = Paint.Style.STROKE
        strokeWidth = 1f
    }

    private val drawPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1E293B")
        style = Paint.Style.STROKE
        strokeWidth = 8f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val paths = mutableListOf<Path>()
    private var currentPath: Path? = null
    private val gridSpacingPx = 24f.dpToPx(context)
    private var scaleFactor = 1f
    private var offsetX = 0f
    private var offsetY = 0f
    private var transformStartDistance = 0f
    private var transformStartScale = 1f
    private var transformStartOffsetX = 0f
    private var transformStartOffsetY = 0f
    private var transformStartFocusX = 0f
    private var transformStartFocusY = 0f
    private var isTransforming = false

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.save()
        canvas.translate(offsetX, offsetY)
        canvas.scale(scaleFactor, scaleFactor)
        drawGrid(canvas)
        paths.forEach { canvas.drawPath(it, drawPaint) }
        currentPath?.let { canvas.drawPath(it, drawPaint) }
        canvas.restore()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.pointerCount >= 2) {
            handleTransform(event)
            return true
        }
        if (event.actionMasked == MotionEvent.ACTION_POINTER_UP) {
            isTransforming = false
            return true
        }
        val x = toCanvasX(event.x)
        val y = toCanvasY(event.y)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                isTransforming = false
                currentPath = Path().apply { moveTo(x, y) }
                invalidate()
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (isTransforming) return true
                parent?.requestDisallowInterceptTouchEvent(true)
                currentPath?.lineTo(x, y)
                invalidate()
                return true
            }

            MotionEvent.ACTION_UP -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                currentPath?.let { paths.add(it) }
                currentPath = null
                invalidate()
                performClick()
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                currentPath?.let { paths.add(it) }
                currentPath = null
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        return super.performClick()
    }

    fun clearCanvas() {
        paths.clear()
        currentPath = null
        invalidate()
    }

    fun resetViewport() {
        scaleFactor = 1f
        offsetX = 0f
        offsetY = 0f
        invalidate()
    }

    private fun handleTransform(event: MotionEvent) {
        parent?.requestDisallowInterceptTouchEvent(true)
        currentPath?.let { paths.add(it) }
        currentPath = null
        val distance = pointerDistance(event)
        val focusX = (event.getX(0) + event.getX(1)) / 2f
        val focusY = (event.getY(0) + event.getY(1)) / 2f
        when (event.actionMasked) {
            MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_DOWN -> {
                beginTransform(distance, focusX, focusY)
            }

            MotionEvent.ACTION_MOVE -> {
                if (!isTransforming || transformStartDistance <= 0f) {
                    beginTransform(distance, focusX, focusY)
                    return
                }
                scaleFactor = (transformStartScale * (distance / transformStartDistance))
                    .coerceIn(MIN_SCALE, MAX_SCALE)
                offsetX = transformStartOffsetX + focusX - transformStartFocusX
                offsetY = transformStartOffsetY + focusY - transformStartFocusY
                invalidate()
            }

            MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isTransforming = false
                parent?.requestDisallowInterceptTouchEvent(false)
            }
        }
    }

    private fun beginTransform(distance: Float, focusX: Float, focusY: Float) {
        isTransforming = true
        transformStartDistance = distance
        transformStartScale = scaleFactor
        transformStartOffsetX = offsetX
        transformStartOffsetY = offsetY
        transformStartFocusX = focusX
        transformStartFocusY = focusY
    }

    private fun pointerDistance(event: MotionEvent): Float {
        if (event.pointerCount < 2) return 0f
        return hypot(event.getX(0) - event.getX(1), event.getY(0) - event.getY(1))
    }

    private fun toCanvasX(x: Float): Float = (x - offsetX) / scaleFactor

    private fun toCanvasY(y: Float): Float = (y - offsetY) / scaleFactor

    private fun drawGrid(canvas: Canvas) {
        if (width <= 0 || height <= 0) return
        val canvasWidth = width / scaleFactor
        val canvasHeight = height / scaleFactor
        var x = -offsetX / scaleFactor % gridSpacingPx
        while (x <= canvasWidth) {
            canvas.drawLine(x, 0f, x, canvasHeight, gridPaint)
            x += gridSpacingPx
        }
        var y = -offsetY / scaleFactor % gridSpacingPx
        while (y <= canvasHeight) {
            canvas.drawLine(0f, y, canvasWidth, y, gridPaint)
            y += gridSpacingPx
        }
    }

    private companion object {
        const val MIN_SCALE = 0.75f
        const val MAX_SCALE = 2.8f
    }
}
