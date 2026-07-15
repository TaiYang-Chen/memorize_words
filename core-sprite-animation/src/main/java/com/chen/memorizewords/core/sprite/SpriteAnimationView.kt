package com.chen.memorizewords.core.sprite

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import kotlin.math.min

class SpriteAnimationView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val destination = RectF()
    private var frame: Bitmap? = null
    private var destinationFrameWidth = 0
    private var destinationFrameHeight = 0

    internal fun swapFrame(bitmap: Bitmap?) {
        checkMainThread()
        frame = bitmap
        if (bitmap == null) {
            destinationFrameWidth = 0
            destinationFrameHeight = 0
            destination.setEmpty()
        } else if (
            bitmap.width != destinationFrameWidth ||
            bitmap.height != destinationFrameHeight
        ) {
            updateDestination(bitmap)
        }
        invalidate()
    }

    internal fun currentFrame(): Bitmap? = frame

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val bitmap = frame ?: return
        if (bitmap.isRecycled || width <= 0 || height <= 0) return
        canvas.drawBitmap(bitmap, null, destination, paint)
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        frame?.let(::updateDestination)
    }

    private fun updateDestination(bitmap: Bitmap) {
        destinationFrameWidth = bitmap.width
        destinationFrameHeight = bitmap.height
        if (width <= 0 || height <= 0) {
            destination.setEmpty()
            return
        }
        val scale = min(width.toFloat() / bitmap.width, height.toFloat() / bitmap.height)
        val drawWidth = bitmap.width * scale
        val drawHeight = bitmap.height * scale
        val left = (width - drawWidth) / 2f
        val top = (height - drawHeight) / 2f
        destination.set(left, top, left + drawWidth, top + drawHeight)
    }

    private fun checkMainThread() {
        check(android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
            "SpriteAnimationView must be updated on the main thread"
        }
    }
}
