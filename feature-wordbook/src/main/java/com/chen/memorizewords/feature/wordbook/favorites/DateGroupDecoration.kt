package com.chen.memorizewords.feature.wordbook.favorites

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.view.View
import androidx.recyclerview.widget.RecyclerView

class DateGroupDecoration(
    private val adapter: FavoritesPagingAdapter
) : RecyclerView.ItemDecoration() {
    private val headerHeight = 72

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#999999")
        textSize = 36f
        typeface = Typeface.DEFAULT_BOLD
    }

    /** 给需要显示日期的 item 预留顶部空间 */
    override fun getItemOffsets(
        outRect: Rect,
        view: View,
        parent: RecyclerView,
        state: RecyclerView.State
    ) {
        val pos = parent.getChildAdapterPosition(view)
        if (pos == RecyclerView.NO_POSITION) return

        val curDate = adapter.getDateSafely(pos)
        val preDate = adapter.getDateSafely(pos - 1)

        if (curDate != null && curDate != preDate) {
            outRect.top = headerHeight
        }
    }

    /** 在预留的区域内绘制日期 */
    override fun onDraw(
        c: Canvas,
        parent: RecyclerView,
        state: RecyclerView.State
    ) {
        for (i in 0 until parent.childCount) {
            val view = parent.getChildAt(i)
            val pos = parent.getChildAdapterPosition(view)

            if (pos == RecyclerView.NO_POSITION) continue

            val curDate = adapter.getDateSafely(pos)
            val preDate = adapter.getDateSafely(pos - 1)

            if (curDate != null && curDate != preDate) {
                val left = view.paddingLeft.toFloat()
                val top = view.top - 10

                c.drawText(
                    curDate,
                    left,
                    top.toFloat(),
                    textPaint
                )
            }
        }
    }
}