package com.chen.memorizewords.feature.learning.ui.fragment

import android.R.attr.lineHeight
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.max

/**
 * 自适应列数的流式布局管理器，完全支持wrap_content
 */
class WaterfallFlowLayoutManager : RecyclerView.LayoutManager() {

    // 间距
    private var horizontalSpacing = 8
    private var verticalSpacing = 16
    private val itemHeight = 69

    // 总高度
    private var totalHeight = 0

    // 记录每个item的位置信息
    private val itemFrames = mutableMapOf<Int, ItemFrame>()

    // 标记是否需要重新计算
    private var isLayoutDirty = true

    override fun generateDefaultLayoutParams(): RecyclerView.LayoutParams {
        return RecyclerView.LayoutParams(
            RecyclerView.LayoutParams.MATCH_PARENT,
            RecyclerView.LayoutParams.WRAP_CONTENT
        )
    }

    override fun onLayoutChildren(recycler: RecyclerView.Recycler, state: RecyclerView.State) {
        if (itemCount == 0) {
            detachAndScrapAttachedViews(recycler)
            totalHeight = paddingTop + paddingBottom
            return
        }

        if (width <= 0) return

        // 如果布局信息已过期，重新计算
        if (isLayoutDirty) {
            calculateItemFrames(recycler)
            isLayoutDirty = false
        }

        // 回收所有视图
        detachAndScrapAttachedViews(recycler)

        // 布局所有可见的item
        for (i in 0 until itemCount) {
            val frame = itemFrames[i] ?: continue

            // 检查item是否在可见范围内
            if (frame.bottom < 0 || frame.top > height) continue

            val view = recycler.getViewForPosition(i)
            addView(view)
            measureChildWithMargins(view, 0, 0)
            layoutDecorated(view, frame.left, frame.top, frame.right, frame.bottom)
        }

        // 对于wrap_content，需要重新请求测量
        if (height != totalHeight) {
            requestLayout()
        }
    }

    /**
     * 计算所有item的位置
     */
    private fun calculateItemFrames(recycler: RecyclerView.Recycler) {
        itemFrames.clear()

        val availableWidth = width - paddingLeft - paddingRight
        var currentX = paddingLeft
        var currentY = paddingTop

        for (i in 0 until itemCount) {
            val view = recycler.getViewForPosition(i)

            // 测量item
            measureChildWithMargins(view, 0, 0)
            val viewWidth = getDecoratedMeasuredWidth(view)

            // 如果当前行放不下，换行
            if (currentX + viewWidth > availableWidth + paddingLeft && currentX > paddingLeft) {
                currentY += itemHeight + verticalSpacing
                currentX = paddingLeft
            }

            // 记录item位置
            itemFrames[i] = ItemFrame(
                currentX,
                currentY,
                currentX + viewWidth,
                currentY + itemHeight
            )

            // 更新位置
            currentX += viewWidth + horizontalSpacing

            // 回收view，我们只是计算位置
            recycler.recycleView(view)
        }

        // 计算总高度
        totalHeight = currentY - itemHeight
    }

    override fun onMeasure(recycler: RecyclerView.Recycler, state: RecyclerView.State, widthSpec: Int, heightSpec: Int) {
        val widthSize = View.MeasureSpec.getSize(widthSpec)
        val widthMode = View.MeasureSpec.getMode(widthSpec)
        val heightSize = View.MeasureSpec.getSize(heightSpec)
        val heightMode = View.MeasureSpec.getMode(heightSpec)

        // 如果宽度为0或布局信息已过期，重新计算
        if (width == 0 || isLayoutDirty) {
            calculateItemFrames(recycler)
            isLayoutDirty = false
        }

        var measuredWidth = widthSize
        var measuredHeight = heightSize

        // 处理宽度
        when (widthMode) {
            View.MeasureSpec.EXACTLY -> {
                measuredWidth = widthSize
            }
            View.MeasureSpec.AT_MOST, View.MeasureSpec.UNSPECIFIED -> {
                measuredWidth = width.coerceAtMost(widthSize)
            }
        }

        // 处理高度 - 这是关键部分
        when (heightMode) {
            View.MeasureSpec.EXACTLY -> {
                measuredHeight = heightSize
            }
            View.MeasureSpec.AT_MOST -> {
                // wrap_content模式，使用计算出的总高度，但不超过最大允许高度
                measuredHeight = totalHeight.coerceAtMost(heightSize)
            }
            View.MeasureSpec.UNSPECIFIED -> {
                // 未指定高度，使用计算出的总高度
                measuredHeight = totalHeight
            }
        }

        // 设置测量尺寸
        setMeasuredDimension(measuredWidth, measuredHeight)
    }

    override fun canScrollVertically(): Boolean {
        // 当RecyclerView高度为wrap_content时，不需要滚动
        return height < totalHeight
    }

    override fun scrollVerticallyBy(dy: Int, recycler: RecyclerView.Recycler, state: RecyclerView.State): Int {
        // 当RecyclerView高度为wrap_content时，不需要滚动
        if (height >= totalHeight) return 0

        val scrollRange = totalHeight - height
        if (scrollRange <= 0) return 0

        var travel = dy
        if (verticalScrollOffset + dy < 0) {
            travel = -verticalScrollOffset
        } else if (verticalScrollOffset + dy > scrollRange) {
            travel = scrollRange - verticalScrollOffset
        }

        if (travel == 0) return 0

        verticalScrollOffset += travel
        offsetChildrenVertical(-travel)
        return travel
    }

    // 垂直滚动偏移
    private var verticalScrollOffset = 0

    /**
     * 设置水平间距
     */
    fun setHorizontalSpacing(spacing: Int) {
        horizontalSpacing = spacing
        isLayoutDirty = true
        requestLayout()
    }

    /**
     * 设置垂直间距
     */
    fun setVerticalSpacing(spacing: Int) {
        verticalSpacing = spacing
        isLayoutDirty = true
        requestLayout()
    }

    /**
     * Item位置信息数据类
     */
    private data class ItemFrame(val left: Int, val top: Int, val right: Int, val bottom: Int)

    override fun onItemsChanged(recyclerView: RecyclerView) {
        super.onItemsChanged(recyclerView)
        isLayoutDirty = true
    }

    override fun onItemsAdded(recyclerView: RecyclerView, positionStart: Int, itemCount: Int) {
        super.onItemsAdded(recyclerView, positionStart, itemCount)
        isLayoutDirty = true
    }

    override fun onItemsRemoved(recyclerView: RecyclerView, positionStart: Int, itemCount: Int) {
        super.onItemsRemoved(recyclerView, positionStart, itemCount)
        isLayoutDirty = true
    }
}