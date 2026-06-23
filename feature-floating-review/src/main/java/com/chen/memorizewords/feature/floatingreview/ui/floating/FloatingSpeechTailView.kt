package com.chen.memorizewords.feature.floatingreview.ui.floating

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View

class FloatingSpeechTailView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var placement: FloatingSpeechPlacement = FloatingSpeechPlacement.ABOVE_PET
        set(value) {
            if (field == value) return
            field = value
            invalidate()
        }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
        setShadowLayer(8f, 0f, 4f, 0x18000000)
    }
    private val fillPath = Path()

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        fillPath.reset()

        if (placement == FloatingSpeechPlacement.ABOVE_PET) {
            buildDownwardTail()
        } else {
            buildUpwardTail()
        }

        canvas.drawPath(fillPath, fillPaint)
    }

    private fun buildDownwardTail() {
        val w = width.toFloat()
        val h = height.toFloat()
        fillPath.moveTo(w * 0.04f, 0f)
        fillPath.cubicTo(w * 0.22f, 0f, w * 0.42f, 0f, w * 0.58f, 0f)
        fillPath.cubicTo(w * 0.60f, h * 0.42f, w * 0.78f, h * 0.78f, w * 0.98f, h * 0.95f)
        fillPath.cubicTo(w * 0.70f, h, w * 0.38f, h * 0.78f, w * 0.20f, h * 0.36f)
        fillPath.cubicTo(w * 0.14f, h * 0.20f, w * 0.10f, h * 0.06f, w * 0.04f, 0f)
        fillPath.close()
    }

    private fun buildUpwardTail() {
        val w = width.toFloat()
        val h = height.toFloat()
        fillPath.moveTo(w * 0.04f, h)
        fillPath.cubicTo(w * 0.22f, h, w * 0.42f, h, w * 0.58f, h)
        fillPath.cubicTo(w * 0.60f, h * 0.58f, w * 0.78f, h * 0.22f, w * 0.98f, h * 0.05f)
        fillPath.cubicTo(w * 0.70f, 0f, w * 0.38f, h * 0.22f, w * 0.20f, h * 0.64f)
        fillPath.cubicTo(w * 0.14f, h * 0.80f, w * 0.10f, h * 0.94f, w * 0.04f, h)
        fillPath.close()
    }
}
