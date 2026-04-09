package com.chen.memorizewords.feature.wordbook.wordlist

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import com.chen.memorizewords.domain.model.words.WordListRow

class GroupCardDecoration(
    context: Context,
    private val adapter: WordPagingAdapter
) : RecyclerView.ItemDecoration() {

    private val density = context.resources.displayMetrics.density

    private val cardRadius = 12f * density
    private val cardMarginH = 0f * density
    private val cardMarginV = 0f * density
    private val cardPaddingTop = 0f * density   // 给字母让位置

    private val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFE0E0E0.toInt()
        strokeWidth = 0.5f * density
    }

    private val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
    }

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 16f * context.resources.displayMetrics.scaledDensity
        color = 0xFF666666.toInt()
        typeface = Typeface.DEFAULT_BOLD
    }

    private fun peek(pos: Int): WordListRow? =
        if (pos in 0 until adapter.itemCount) adapter.peek(pos) else null

    private fun isFirstInGroup(pos: Int): Boolean {
        val cur = peek(pos) ?: return false
        val prev = peek(pos - 1)
        return prev == null || prev.groupChar != cur.groupChar
    }

    private fun isLastInGroup(pos: Int): Boolean {
        val cur = peek(pos) ?: return false
        val next = peek(pos + 1)
        return next == null || next.groupChar != cur.groupChar
    }

    override fun getItemOffsets(
        outRect: Rect,
        view: View,
        parent: RecyclerView,
        state: RecyclerView.State
    ) {
        val pos = parent.getChildAdapterPosition(view)
        if (pos == RecyclerView.NO_POSITION) return

        if (isFirstInGroup(pos)) {
            // 组与组之间的真实间距
            outRect.top = (40 * density).toInt()
        }
    }


    override fun onDraw(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {

        for (i in 0 until parent.childCount) {
            val child = parent.getChildAt(i)
            val pos = parent.getChildAdapterPosition(child)
            if (pos == RecyclerView.NO_POSITION) continue

            val item = peek(pos) ?: continue

            val isFirst = isFirstInGroup(pos)
            val isLast = isLastInGroup(pos)

            val left = cardMarginH
            val right = parent.width - cardMarginH

            val top =
                if (isFirst) child.top - cardPaddingTop
                else child.top.toFloat()

            val bottom =
                if (isLast) child.bottom + cardMarginV
                else child.bottom.toFloat()

            // 白色圆角卡片
            val path = createCardPath(
                left = left,
                top = top,
                right = right,
                bottom = bottom,
                isFirst = isFirst,
                isLast = isLast
            )

            c.drawPath(path, cardPaint)

            // 组内分隔线
            if (!isFirst) {
                c.drawLine(
                    left,
                    child.top.toFloat(),
                    right,
                    child.top.toFloat(),
                    dividerPaint
                )
            }

            // 卡片左上角字母（只画一次）
            if (isFirst) {
                c.drawText(
                    item.groupChar.toString(),
                    left + 8f * density,
                    top - 16f * density,
                    labelPaint
                )
            }
        }
    }

    private fun createCardPath(
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        isFirst: Boolean,
        isLast: Boolean
    ): Path {
        val radii = FloatArray(8) { 0f }

        if (isFirst) {
            radii[0] = cardRadius   // 左上
            radii[1] = cardRadius
            radii[2] = cardRadius   // 右上
            radii[3] = cardRadius
        }

        if (isLast) {
            radii[4] = cardRadius   // 右下
            radii[5] = cardRadius
            radii[6] = cardRadius   // 左下
            radii[7] = cardRadius
        }

        return Path().apply {
            addRoundRect(
                RectF(left, top, right, bottom),
                radii,
                Path.Direction.CW
            )
        }
    }

}
