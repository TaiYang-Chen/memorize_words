package com.chen.memorizewords.core.ui.insets

import android.app.Activity
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.util.DisplayMetrics
import android.util.Log
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.platform.app.InstrumentationRegistry

data class RuntimeWindowMetricsReport(
    val manufacturer: String,
    val model: String,
    val apiLevel: Int,
    val physicalWidthPx: Int,
    val physicalHeightPx: Int,
    val windowBounds: Rect,
    val screenWidthDp: Int,
    val screenHeightDp: Int,
    val densityDpi: Int,
    val fontScale: Float,
    val navigationMode: String,
    val statusBars: Insets,
    val navigationBars: Insets,
    val displayCutout: Insets,
    val systemGestures: Insets,
    val ime: Insets
) {
    fun toReportLine(label: String): String = buildString {
        append("MOBILE_WINDOW_METRICS")
        append("|label=").append(label)
        append("|manufacturer=").append(manufacturer)
        append("|model=").append(model)
        append("|api=").append(apiLevel)
        append("|physicalPx=").append(physicalWidthPx).append('x').append(physicalHeightPx)
        append("|windowPx=")
        append(windowBounds.width()).append('x').append(windowBounds.height())
        append("@(").append(windowBounds.left).append(',').append(windowBounds.top).append(')')
        append("|screenDp=").append(screenWidthDp).append('x').append(screenHeightDp)
        append("|densityDpi=").append(densityDpi)
        append("|fontScale=").append(fontScale)
        append("|navigationMode=").append(navigationMode)
        append("|statusBars=").append(statusBars.toCompactString())
        append("|navigationBars=").append(navigationBars.toCompactString())
        append("|displayCutout=").append(displayCutout.toCompactString())
        append("|systemGestures=").append(systemGestures.toCompactString())
        append("|ime=").append(ime.toCompactString())
    }
}

/**
 * Android-test-only collector. Keep this out of main sources so production has no device branches.
 */
object RuntimeWindowMetricsReporter {

    const val REPORT_KEY = "mobile_window_metrics"
    private const val TAG = "WindowMetricsReport"

    fun capture(activity: Activity): RuntimeWindowMetricsReport {
        val configuration = activity.resources.configuration
        val displayMetrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        activity.windowManager.defaultDisplay.getRealMetrics(displayMetrics)

        val windowBounds = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Rect(activity.windowManager.currentWindowMetrics.bounds)
        } else {
            Rect(0, 0, displayMetrics.widthPixels, displayMetrics.heightPixels)
        }
        val rootInsets = ViewCompat.getRootWindowInsets(activity.window.decorView)
        val statusBars = rootInsets?.getInsets(WindowInsetsCompat.Type.statusBars()) ?: Insets.NONE
        val navigationBars =
            rootInsets?.getInsets(WindowInsetsCompat.Type.navigationBars()) ?: Insets.NONE
        val displayCutout =
            rootInsets?.getInsets(WindowInsetsCompat.Type.displayCutout()) ?: Insets.NONE
        val systemGestures =
            rootInsets?.getInsets(WindowInsetsCompat.Type.systemGestures()) ?: Insets.NONE
        val ime = rootInsets?.getInsets(WindowInsetsCompat.Type.ime()) ?: Insets.NONE

        return RuntimeWindowMetricsReport(
            manufacturer = Build.MANUFACTURER.orEmpty(),
            model = Build.MODEL.orEmpty(),
            apiLevel = Build.VERSION.SDK_INT,
            physicalWidthPx = displayMetrics.widthPixels,
            physicalHeightPx = displayMetrics.heightPixels,
            windowBounds = windowBounds,
            screenWidthDp = configuration.screenWidthDp,
            screenHeightDp = configuration.screenHeightDp,
            densityDpi = displayMetrics.densityDpi,
            fontScale = configuration.fontScale,
            navigationMode = if (
                navigationBars.left > 0 || navigationBars.right > 0 || navigationBars.bottom > 0
            ) {
                "navigation-bar-visible"
            } else {
                "gesture-or-hidden-navigation"
            },
            statusBars = statusBars,
            navigationBars = navigationBars,
            displayCutout = displayCutout,
            systemGestures = systemGestures,
            ime = ime
        )
    }

    fun emit(activity: Activity, label: String) {
        val line = capture(activity).toReportLine(label)
        Log.i(TAG, line)
        println(line)
        InstrumentationRegistry.getInstrumentation().sendStatus(
            0,
            Bundle().apply { putString(REPORT_KEY, line) }
        )
    }
}

private fun Insets.toCompactString(): String = "$left,$top,$right,$bottom"
