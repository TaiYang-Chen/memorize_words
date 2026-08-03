package com.chen.memorizewords.core.ui.insets

import android.app.Activity
import android.app.Dialog
import android.graphics.Rect
import android.view.View
import android.view.Window
import android.view.WindowManager
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.DialogFragment
import com.chen.memorizewords.core.ui.R

/**
 * The four physical edges that can receive window insets.
 */
enum class PhoneInsetSide {
    START,
    TOP,
    END,
    BOTTOM
}

private data class OriginalPadding(
    val start: Int,
    val top: Int,
    val end: Int,
    val bottom: Int
)

internal data class PhoneRelativeInsets(
    val start: Int,
    val top: Int,
    val end: Int,
    val bottom: Int
)

/**
 * Enables edge-to-edge drawing and resize-on-IME behavior for a phone activity window.
 */
fun Activity.configurePhoneWindowForInsets() {
    window.configurePhoneWindowForInsets()
}

/**
 * Applies the window flags used by phone activity hosts without changing their chosen bar colors.
 */
@Suppress("DEPRECATION") // Required for API 25-29 IME resize compatibility.
fun Window.configurePhoneWindowForInsets() {
    WindowCompat.setDecorFitsSystemWindows(this, false)
    setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
}

/**
 * Lets a platform or Material dialog resize for the IME without overriding its inset policy.
 *
 * Regular dialogs and Material bottom sheets already own their system-bar handling. Applying
 * additional content padding here would double-count the navigation bar on some Android versions.
 */
@Suppress("DEPRECATION") // Required for API 25-29 IME resize compatibility.
fun DialogFragment.configurePhoneDialogForIme() {
    dialog?.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
}

/**
 * Lets a directly created platform or Material dialog resize for the IME.
 */
@Suppress("DEPRECATION") // Required for API 25-29 IME resize compatibility.
fun Dialog.configurePhoneDialogForIme() {
    window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
}

/**
 * Adds safe drawing insets to padding, preserving the padding defined by the layout.
 *
 * The listener returns the original insets so nested views can opt into a more specific policy.
 */
fun View.applyPhoneWindowInsets(
    sides: Set<PhoneInsetSide> = setOf(
        PhoneInsetSide.START,
        PhoneInsetSide.TOP,
        PhoneInsetSide.END,
        PhoneInsetSide.BOTTOM
    ),
    includeIme: Boolean = true,
    includeSystemGestures: Boolean = false
) {
    val original = originalPadding()
    ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
        val safeInsets = insets.phoneSafeInsets(
            includeIme = includeIme,
            includeSystemGestures = includeSystemGestures
        )
        val relativeInsets = safeInsets.toPhoneRelativeInsets(
            sides = sides,
            layoutDirection = view.layoutDirection
        )
        view.setPaddingRelative(
            original.start + relativeInsets.start,
            original.top + relativeInsets.top,
            original.end + relativeInsets.end,
            original.bottom + relativeInsets.bottom
        )
        insets
    }
    ViewCompat.requestApplyInsets(this)
}

/**
 * Returns this view's drawable bounds after subtracting the protected window edges.
 *
 * This is intended for positioned transient surfaces, such as a [android.widget.PopupWindow].
 * Normal activity content should use [applyPhoneWindowInsets] instead so its layout remains
 * responsive when the window changes size.
 */
fun View.phoneSafeDrawingBounds(
    includeIme: Boolean = true,
    includeSystemGestures: Boolean = false
): Rect {
    val bounds = Rect(0, 0, width, height)
    val safeInsets = ViewCompat.getRootWindowInsets(this)?.phoneSafeInsets(
        includeIme = includeIme,
        includeSystemGestures = includeSystemGestures
    ) ?: return bounds
    return Rect(
        bounds.left + safeInsets.left,
        bounds.top + safeInsets.top,
        bounds.right - safeInsets.right,
        bounds.bottom - safeInsets.bottom
    )
}

internal fun WindowInsetsCompat.phoneSafeInsets(
    includeIme: Boolean,
    includeSystemGestures: Boolean
): Insets {
    val sources = buildList {
        add(getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()))
        if (includeSystemGestures) add(getInsets(WindowInsetsCompat.Type.systemGestures()))
        if (includeIme) add(getInsets(WindowInsetsCompat.Type.ime()))
    }
    return maxInsets(sources)
}

internal fun maxInsets(insets: Iterable<Insets>): Insets {
    var left = 0
    var top = 0
    var right = 0
    var bottom = 0
    insets.forEach { value ->
        left = maxOf(left, value.left)
        top = maxOf(top, value.top)
        right = maxOf(right, value.right)
        bottom = maxOf(bottom, value.bottom)
    }
    return Insets.of(left, top, right, bottom)
}

internal fun Insets.toPhoneRelativeInsets(
    sides: Set<PhoneInsetSide>,
    layoutDirection: Int
): PhoneRelativeInsets {
    val isRtl = layoutDirection == View.LAYOUT_DIRECTION_RTL
    val start = if (isRtl) right else left
    val end = if (isRtl) left else right
    return PhoneRelativeInsets(
        start = start.takeIf { PhoneInsetSide.START in sides } ?: 0,
        top = top.takeIf { PhoneInsetSide.TOP in sides } ?: 0,
        end = end.takeIf { PhoneInsetSide.END in sides } ?: 0,
        bottom = bottom.takeIf { PhoneInsetSide.BOTTOM in sides } ?: 0
    )
}

private fun View.originalPadding(): OriginalPadding {
    return (getTag(R.id.core_ui_original_padding) as? OriginalPadding)
        ?: OriginalPadding(
            start = paddingStart,
            top = paddingTop,
            end = paddingEnd,
            bottom = paddingBottom
        ).also { setTag(R.id.core_ui_original_padding, it) }
}
