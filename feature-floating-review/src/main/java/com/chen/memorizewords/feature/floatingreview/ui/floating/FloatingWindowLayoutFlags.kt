package com.chen.memorizewords.feature.floatingreview.ui.floating

import android.view.WindowManager

internal fun floatingBallWindowFlags(): Int {
    return WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
}

internal fun floatingCardWindowFlags(): Int {
    return WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
}
