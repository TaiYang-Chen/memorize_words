package com.chen.memorizewords.feature.wordbook.favorites

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.chen.memorizewords.feature.wordbook.R

class DateGroupDecoration(
    private val adapter: FavoritesPagingAdapter
) : RecyclerView.ItemDecoration() {

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.DEFAULT_BOLD
    }
    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val badgeBounds = RectF()

    override fun getItemOffsets(
        outRect: Rect,
        view: View,
        parent: RecyclerView,
        state: RecyclerView.State
    ) {
        val position = parent.getChildAdapterPosition(view)
        if (position == RecyclerView.NO_POSITION) return

        val currentDate = adapter.getDateSafely(position)
        val previousDate = adapter.getDateSafely(position - 1)

        if (currentDate != null && currentDate != previousDate) {
            outRect.top = view.resources.getDimensionPixelSize(
                R.dimen.feature_wordbook_favorites_date_header_height
            )
        }
    }

    override fun onDraw(
        canvas: Canvas,
        parent: RecyclerView,
        state: RecyclerView.State
    ) {
        val resources = parent.resources
        val headerHeight = resources.getDimensionPixelSize(
            R.dimen.feature_wordbook_favorites_date_header_height
        )
        val badgeHeight = resources.getDimension(
            R.dimen.feature_wordbook_favorites_date_badge_height
        )
        val badgePaddingHorizontal = resources.getDimension(
            R.dimen.feature_wordbook_favorites_date_badge_padding_horizontal
        )
        val badgeTopPadding = ((headerHeight - badgeHeight) / 2f).coerceAtLeast(0f)

        backgroundPaint.color = ContextCompat.getColor(
            parent.context,
            R.color.feature_wordbook_favorites_date_background
        )
        textPaint.color = ContextCompat.getColor(
            parent.context,
            R.color.feature_wordbook_favorites_date_text
        )
        textPaint.textSize = resources.getDimension(
            R.dimen.feature_wordbook_favorites_date_text_size
        )

        for (index in 0 until parent.childCount) {
            val view = parent.getChildAt(index)
            val position = parent.getChildAdapterPosition(view)
            if (position == RecyclerView.NO_POSITION) continue

            val currentDate = adapter.getDateSafely(position)
            val previousDate = adapter.getDateSafely(position - 1)

            if (currentDate != null && currentDate != previousDate) {
                val left = view.left.toFloat()
                val top = view.top - headerHeight + badgeTopPadding
                val width = textPaint.measureText(currentDate) + badgePaddingHorizontal * 2
                badgeBounds.set(left, top, left + width, top + badgeHeight)

                canvas.drawRoundRect(
                    badgeBounds,
                    badgeHeight / 2f,
                    badgeHeight / 2f,
                    backgroundPaint
                )

                val fontMetrics = textPaint.fontMetrics
                val baseline = top + (badgeHeight - fontMetrics.ascent - fontMetrics.descent) / 2f
                canvas.drawText(currentDate, left + badgePaddingHorizontal, baseline, textPaint)
            }
        }
    }
}
