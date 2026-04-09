package com.chen.memorizewords.feature.wordbook.custom

import android.R.attr.height
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import com.chen.memorizewords.feature.wordbook.R

class MultiProgressView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var max = 100
    private var progress1 = 0
    private var progress2 = 0

    private var bgColor = Color.parseColor("#EDEDED")
    private var progress1Color = Color.parseColor("#FFA000")
    private var progress2Color = Color.parseColor("#FF6F00")

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    init {
        attrs?.let {
            val ta = context.obtainStyledAttributes(it, R.styleable.MultiProgressView)
            max = ta.getInt(R.styleable.MultiProgressView_max, max)
            progress1 = ta.getInt(R.styleable.MultiProgressView_progress1, progress1)
            progress2 = ta.getInt(R.styleable.MultiProgressView_progress2, progress2)
            bgColor = ta.getColor(R.styleable.MultiProgressView_bgColor, bgColor)
            progress1Color = ta.getColor(R.styleable.MultiProgressView_progress1Color, progress1Color)
            progress2Color = ta.getColor(R.styleable.MultiProgressView_progress2Color, progress2Color)
            ta.recycle()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val radius = height / 2f
        val fullRect = RectF(0f, 0f, width.toFloat(), height.toFloat())

        // 1. 背景
        paint.color = bgColor
        canvas.drawRoundRect(fullRect, radius, radius, paint)

        // 2. 第一个进度
        val p1Width = width * progress1 / max.toFloat()
        if (p1Width > 0) {
            paint.color = progress1Color
            canvas.drawRoundRect(
                RectF(0f, 0f, p1Width, height.toFloat()),
                radius, radius, paint
            )
        }

        // 3. 第二个进度（叠加）
        val p2Width = width * progress2 / max.toFloat()
        if (p2Width > 0) {
            paint.color = progress2Color
            canvas.drawRoundRect(
                RectF(0f, 0f, p2Width, height.toFloat()),
                radius, radius, paint
            )
        }
    }

    fun setProgress(p1: Int, p2: Int) {
        progress1 = p1
        progress2 = p2
        invalidate()
    }

    fun setMax(value: Int) {
        max = value
        invalidate()
    }

    fun setProgress1(value: Int) {
        progress1 = value
        invalidate()
    }

    fun setProgress2(value: Int) {
        progress2 = value
        invalidate()
    }
}
