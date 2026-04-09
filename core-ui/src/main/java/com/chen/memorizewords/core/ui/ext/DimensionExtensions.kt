package com.chen.memorizewords.core.ui.ext

import android.content.Context
import android.util.TypedValue

fun Int.spToPx(context: Context): Int {
    return this.toFloat().spToPx(context).toInt()
}

fun Float.spToPx(context: Context): Float {
    return TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_SP,
        this,
        context.resources.displayMetrics
    )
}

fun Float.dpToPx(context: Context): Float {
    return TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        this,
        context.resources.displayMetrics
    )
}

fun Int.dpToPx(context: Context): Int {
    return this.toFloat().dpToPx(context).toInt()
}