package com.chen.memorizewords.feature.wordbook.wordlist

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.chen.memorizewords.domain.model.words.WordListRow
import com.chen.memorizewords.feature.wordbook.R
import kotlin.math.min

class StickyGroupHeaderDecoration(
    context: Context,
    private val adapter: WordPagingAdapter
) : RecyclerView.ItemDecoration() {

    private val density = context.resources.displayMetrics.density
    private val scaledDensity = context.resources.displayMetrics.scaledDensity

    private val headerHeight = 36f * density
    private val shadowHeight = 8f * density
    private val textMarginStart = 12f * density

    /** Header 背景 */
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
    }

    /** Header 文本 */
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 16f * scaledDensity
        color = 0xFF666666.toInt()
        typeface = Typeface.DEFAULT_BOLD
    }

    /** Header 阴影（渐变） */
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(
            0f, 0f,
            0f, shadowHeight,
            0x33000000,
            0x00000000,
            Shader.TileMode.CLAMP
        )
    }

    private fun peekSafe(pos: Int): WordListRow? {
        if (pos == RecyclerView.NO_POSITION) return null
        if (adapter.itemCount == 0) return null
        if (pos !in 0 until adapter.itemCount) return null
        return adapter.peek(pos)
    }

    /** 是否 RecyclerView 在顶部 */
    private fun isAtTop(parent: RecyclerView): Boolean {
        val firstChild = parent.getChildAt(0) ?: return true
        return firstChild.top >= parent.paddingTop
    }

    override fun onDrawOver(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {

        // ===== 1️⃣ 空数据保护 =====
        if (adapter.itemCount == 0) return

        val firstChild = parent.getChildAt(0) ?: return
        val firstPos = parent.getChildAdapterPosition(firstChild)
        if (firstPos == RecyclerView.NO_POSITION) return

        val curr = peekSafe(firstPos) ?: return
        val currChar = curr.groupChar

        var offset = headerHeight

        // ===== 2️⃣ 顶起效果（下一个分组顶上来）=====
        for (i in 1 until parent.childCount) {
            val child = parent.getChildAt(i)
            val pos = parent.getChildAdapterPosition(child)
            val next = peekSafe(pos) ?: continue

            if (next.groupChar != currChar) {
                offset = min(offset, child.top.toFloat())
                break
            }
        }

        // ===== 3️⃣ Header 背景 =====
        c.drawRect(
            0f,
            0f,
            parent.width.toFloat(),
            offset,
            bgPaint
        )

        // ===== 4️⃣ 阴影（非顶部 & 非顶起状态）=====
        if (!isAtTop(parent) && offset == headerHeight) {
            c.drawRect(
                0f,
                headerHeight,
                parent.width.toFloat(),
                headerHeight + shadowHeight,
                shadowPaint
            )
        }

        // ===== 5️⃣ Header 文本 =====
        val textY = offset - headerHeight / 2 + textPaint.textSize / 2
        c.drawText(
            currChar.toString(),
            textMarginStart,
            textY,
            textPaint
        )
    }
}
