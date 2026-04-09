package com.chen.memorizewords.feature.learning

import android.graphics.Rect
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class LinearSpacingItemDecoration(
    private val spacing: Int
) : RecyclerView.ItemDecoration() {

    val firstRect: Rect = Rect()
    val lastRect: Rect = Rect()

    override fun getItemOffsets(
        outRect: Rect,
        view: View,
        parent: RecyclerView,
        state: RecyclerView.State
    ) {
        val position = parent.getChildAdapterPosition(view)
        val layoutManager = parent.layoutManager

        if (layoutManager is LinearLayoutManager) {
            val orientation = layoutManager.orientation

            if (orientation == LinearLayoutManager.VERTICAL) {
                if (position > 0) {
                    outRect.top = spacing
                }
            } else {
                if (position > 0) {
                    outRect.left = spacing
                }
            }
        }

        if (position == 0) {
            outRect.left += firstRect.left
            outRect.top += firstRect.top
            outRect.right += firstRect.right
            outRect.bottom += firstRect.bottom
        }

        if (position == parent.adapter?.itemCount?.minus(1)) {
            outRect.left += lastRect.left
            outRect.top += lastRect.top
            outRect.right += lastRect.right
            outRect.bottom += lastRect.bottom
        }
    }
}