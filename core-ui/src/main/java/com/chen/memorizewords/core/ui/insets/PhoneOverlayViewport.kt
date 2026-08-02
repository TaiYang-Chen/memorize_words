package com.chen.memorizewords.core.ui.insets

import android.content.res.Resources
import android.graphics.Rect
import android.os.Build
import android.util.DisplayMetrics
import android.view.WindowInsets
import android.view.WindowManager
import androidx.core.graphics.Insets

/**
 * Geometry for an application overlay that needs both physical movement limits and
 * inset-aware content limits.
 */
data class PhoneOverlayViewport(
    val physicalBounds: Rect,
    val contentSafeBounds: Rect
)

/**
 * Resolves overlay geometry without conflating the physical display edge with the
 * protected region used by interactive content.
 */
fun WindowManager.phoneOverlayViewport(
    resources: Resources,
    rootWindowInsets: WindowInsets? = null
): PhoneOverlayViewport {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val displayMetrics = maximumWindowMetrics
        val physicalBounds = Rect(displayMetrics.bounds)
        val systemInsets = displayMetrics.windowInsets.getInsetsIgnoringVisibility(
            WindowInsets.Type.statusBars() or
                WindowInsets.Type.navigationBars() or
                WindowInsets.Type.displayCutout()
        )
        val gestureInsets = displayMetrics.windowInsets.getInsets(WindowInsets.Type.systemGestures())
        val contentInsets = maxInsets(
            listOf(
                Insets.of(
                    systemInsets.left,
                    systemInsets.top,
                    systemInsets.right,
                    systemInsets.bottom
                ),
                Insets.of(
                    gestureInsets.left,
                    gestureInsets.top,
                    gestureInsets.right,
                    gestureInsets.bottom
                )
            )
        )
        return PhoneOverlayViewport(
            physicalBounds = physicalBounds,
            contentSafeBounds = physicalBounds.insetBy(contentInsets)
        )
    }

    val physicalBounds = legacyPhysicalBounds()
    val contentInsets = maxInsets(
        listOf(
            legacySystemBarInsets(resources, rootWindowInsets),
            legacyDisplayCutoutInsets(rootWindowInsets),
            legacySystemGestureInsets(rootWindowInsets)
        )
    )
    return PhoneOverlayViewport(
        physicalBounds = physicalBounds,
        contentSafeBounds = physicalBounds.insetBy(contentInsets)
    )
}

@Suppress("DEPRECATION")
private fun WindowManager.legacyPhysicalBounds(): Rect {
    val metrics = DisplayMetrics()
    defaultDisplay.getRealMetrics(metrics)
    return Rect(0, 0, metrics.widthPixels, metrics.heightPixels)
}

private fun legacySystemBarInsets(
    resources: Resources,
    rootWindowInsets: WindowInsets?
): Insets {
    val reported = rootWindowInsets?.let { insets ->
        Insets.of(
            maxOf(insets.systemWindowInsetLeft, insets.stableInsetLeft),
            maxOf(insets.systemWindowInsetTop, insets.stableInsetTop),
            maxOf(insets.systemWindowInsetRight, insets.stableInsetRight),
            maxOf(insets.systemWindowInsetBottom, insets.stableInsetBottom)
        )
    } ?: Insets.of(0, 0, 0, 0)
    return Insets.of(
        reported.left,
        maxOf(reported.top, resources.systemDimension("status_bar_height")),
        reported.right,
        maxOf(reported.bottom, resources.systemDimension("navigation_bar_height"))
    )
}

private fun legacyDisplayCutoutInsets(rootWindowInsets: WindowInsets?): Insets {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return Insets.of(0, 0, 0, 0)
    return rootWindowInsets?.displayCutout?.let { cutout ->
        Insets.of(
            cutout.safeInsetLeft,
            cutout.safeInsetTop,
            cutout.safeInsetRight,
            cutout.safeInsetBottom
        )
    } ?: Insets.of(0, 0, 0, 0)
}

private fun legacySystemGestureInsets(rootWindowInsets: WindowInsets?): Insets {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return Insets.of(0, 0, 0, 0)
    return rootWindowInsets?.systemGestureInsets?.let { insets ->
        Insets.of(
            insets.left,
            insets.top,
            insets.right,
            insets.bottom
        )
    } ?: Insets.of(0, 0, 0, 0)
}

private fun Rect.insetBy(insets: Insets): Rect {
    val safeLeft = (this.left + insets.left).coerceIn(this.left, this.right)
    val safeTop = (this.top + insets.top).coerceIn(this.top, this.bottom)
    val safeRight = (this.right - insets.right).coerceIn(safeLeft, this.right)
    val safeBottom = (this.bottom - insets.bottom).coerceIn(safeTop, this.bottom)
    return Rect(safeLeft, safeTop, safeRight, safeBottom)
}

private fun Resources.systemDimension(name: String): Int {
    val resourceId = getIdentifier(name, "dimen", "android")
    return resourceId.takeIf { it != 0 }?.let { getDimensionPixelSize(it) } ?: 0
}
