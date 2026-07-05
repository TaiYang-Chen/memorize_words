package com.chen.memorizewords.core.ui.ext

import android.content.Context
import android.util.TypedValue
import android.view.View
import android.widget.TextView
import androidx.annotation.DimenRes

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

fun Context.dimenPx(@DimenRes id: Int): Int {
    return resources.getDimensionPixelSize(id)
}

fun Context.dimenPxFloat(@DimenRes id: Int): Float {
    return resources.getDimension(id)
}

fun View.dimenPx(@DimenRes id: Int): Int {
    return context.dimenPx(id)
}

fun View.dimenPxFloat(@DimenRes id: Int): Float {
    return context.dimenPxFloat(id)
}

fun TextView.setTextSizeFromDimen(@DimenRes id: Int) {
    setTextSize(TypedValue.COMPLEX_UNIT_PX, context.dimenPxFloat(id))
}
