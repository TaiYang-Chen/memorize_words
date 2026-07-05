package com.chen.memorizewords.feature.user.ui.profile

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.animation.DecelerateInterpolator
import androidx.core.widget.NestedScrollView
import com.chen.memorizewords.core.ui.ext.dpToPx
import kotlin.math.abs

class ElasticNestedScrollView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : NestedScrollView(context, attrs, defStyleAttr) {

    private companion object {
        const val INVALID_POINTER_ID = -1
        const val MAX_TRANSLATION_DP = 72f
        const val DRAG_DAMPING = 0.45f
        const val RETURN_DURATION_MS = 200L
    }

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val maxTranslation = MAX_TRANSLATION_DP.dpToPx(context)
    private val returnInterpolator = DecelerateInterpolator()

    private var activePointerId = INVALID_POINTER_ID
    private var downX = 0f
    private var downY = 0f
    private var lastY = 0f
    private var isVerticalDrag = false
    private var isElasticDragging = false

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                resetTouch(ev)
                cancelReturnAnimation()
            }

            MotionEvent.ACTION_MOVE -> {
                val pointerIndex = ev.findPointerIndex(activePointerId)
                if (pointerIndex < 0) return super.onInterceptTouchEvent(ev)

                val dx = ev.getX(pointerIndex) - downX
                val dy = ev.getY(pointerIndex) - downY
                if (!isVerticalDrag && abs(dy) > touchSlop && abs(dy) > abs(dx)) {
                    isVerticalDrag = true
                }
                if (isVerticalDrag && shouldApplyElastic(dy)) {
                    isElasticDragging = true
                    parent?.requestDisallowInterceptTouchEvent(true)
                    return true
                }
            }

            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> {
                resetTouchState()
            }
        }
        return super.onInterceptTouchEvent(ev)
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                resetTouch(ev)
                cancelReturnAnimation()
            }

            MotionEvent.ACTION_POINTER_UP -> {
                updateActivePointer(ev)
            }

            MotionEvent.ACTION_MOVE -> {
                val pointerIndex = ev.findPointerIndex(activePointerId)
                if (pointerIndex < 0) return super.onTouchEvent(ev)

                val y = ev.getY(pointerIndex)
                val dy = y - lastY
                if (isElasticDragging) {
                    applyElasticDelta(dy)
                    lastY = y
                    return true
                }

                val handled = super.onTouchEvent(ev)
                if (isVerticalMove(ev, pointerIndex) && shouldApplyElastic(dy)) {
                    isElasticDragging = true
                    applyElasticDelta(dy)
                    lastY = y
                    return true
                }

                lastY = y
                return handled
            }

            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> {
                val shouldRelease = isElasticDragging || abs(contentTranslationY()) > 0.5f
                resetTouchState()
                if (shouldRelease) {
                    releaseElastic()
                    return true
                }
            }
        }
        return super.onTouchEvent(ev)
    }

    override fun onDetachedFromWindow() {
        cancelReturnAnimation()
        contentView()?.translationY = 0f
        super.onDetachedFromWindow()
    }

    private fun resetTouch(ev: MotionEvent) {
        activePointerId = ev.getPointerId(0)
        downX = ev.x
        downY = ev.y
        lastY = ev.y
        isVerticalDrag = false
        isElasticDragging = false
    }

    private fun resetTouchState() {
        activePointerId = INVALID_POINTER_ID
        isVerticalDrag = false
        isElasticDragging = false
    }

    private fun updateActivePointer(ev: MotionEvent) {
        val pointerIndex = ev.actionIndex
        if (ev.getPointerId(pointerIndex) != activePointerId) return

        val newPointerIndex = if (pointerIndex == 0) 1 else 0
        activePointerId = ev.getPointerId(newPointerIndex)
        downX = ev.getX(newPointerIndex)
        downY = ev.getY(newPointerIndex)
        lastY = ev.getY(newPointerIndex)
    }

    private fun isVerticalMove(ev: MotionEvent, pointerIndex: Int): Boolean {
        val dx = ev.getX(pointerIndex) - downX
        val dy = ev.getY(pointerIndex) - downY
        if (!isVerticalDrag && abs(dy) > touchSlop && abs(dy) > abs(dx)) {
            isVerticalDrag = true
        }
        return isVerticalDrag
    }

    private fun shouldApplyElastic(dy: Float): Boolean {
        val translationY = contentTranslationY()
        return when {
            translationY > 0f && dy < 0f -> true
            translationY < 0f && dy > 0f -> true
            dy > 0f -> !canScrollVertically(-1)
            dy < 0f -> !canScrollVertically(1)
            else -> false
        }
    }

    private fun applyElasticDelta(dy: Float) {
        val content = contentView() ?: return
        val currentTranslation = content.translationY
        val nextTranslation = currentTranslation + dy * DRAG_DAMPING
        content.translationY = when {
            currentTranslation > 0f && nextTranslation < 0f -> 0f
            currentTranslation < 0f && nextTranslation > 0f -> 0f
            else -> nextTranslation.coerceIn(-maxTranslation, maxTranslation)
        }
    }

    private fun releaseElastic() {
        contentView()
            ?.animate()
            ?.translationY(0f)
            ?.setDuration(RETURN_DURATION_MS)
            ?.setInterpolator(returnInterpolator)
            ?.start()
    }

    private fun cancelReturnAnimation() {
        contentView()?.animate()?.cancel()
    }

    private fun contentTranslationY(): Float = contentView()?.translationY ?: 0f

    private fun contentView() = getChildAt(0)
}
