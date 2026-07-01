package com.chen.memorizewords.feature.learning.ui.practice.listening.view

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import androidx.core.widget.NestedScrollView

class LockableNestedScrollView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : NestedScrollView(context, attrs, defStyleAttr) {

    var isUserScrollEnabled: Boolean = true
        set(value) {
            field = value
            if (!value && scrollY != 0) {
                scrollTo(0, 0)
            }
        }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        return isUserScrollEnabled && super.onInterceptTouchEvent(ev)
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        return isUserScrollEnabled && super.onTouchEvent(ev)
    }

    override fun fling(velocityY: Int) {
        if (isUserScrollEnabled) {
            super.fling(velocityY)
        }
    }
}
