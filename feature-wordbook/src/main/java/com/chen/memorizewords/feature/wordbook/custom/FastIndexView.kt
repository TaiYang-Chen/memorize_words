package com.chen.memorizewords.feature.wordbook.custom

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

class FastIndexView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val letters = ('A'..'Z').toList()

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 12f * resources.displayMetrics.scaledDensity
        color = 0xFF999999.toInt()
        textAlign = Paint.Align.CENTER
    }

    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 12f * resources.displayMetrics.scaledDensity
        color = 0xFF333333.toInt()
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }

    private var itemHeight = 0f
    private var currentIndex = -1

    fun setCurrentLetter(char: Char) {
        currentIndex = letters.indexOf(char)
        invalidate()
    }

    /** 对外回调 */
    var onLetterChanged: ((Char) -> Unit)? = null

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        itemHeight = h.toFloat() / letters.size
    }

    override fun onDraw(canvas: Canvas) {
        val centerX = width / 2f
        letters.forEachIndexed { index, c ->
            val y = itemHeight * index + itemHeight / 2 + textPaint.textSize / 2
            canvas.drawText(
                c.toString(),
                centerX,
                y,
                if (index == currentIndex) highlightPaint else textPaint
            )
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN,
            MotionEvent.ACTION_MOVE -> {
                val index = (event.y / itemHeight).toInt()
                    .coerceIn(0, letters.lastIndex)
                if (index != currentIndex) {
                    currentIndex = index
                    onLetterChanged?.invoke(letters[index])
                    invalidate()
                }
                return true
            }

            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> {
                invalidate()
            }
        }
        return super.onTouchEvent(event)
    }
}
