package com.chen.memorizewords.core.sprite

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout

/**
 * Renderer-neutral container used by the floating-pet window.
 *
 * A session owns the installed child view. Replacing a session atomically replaces the child,
 * allowing WebP v1 and GPU KTX2 v2 packs to share the floating-window business code.
 */
class FloatingPetRenderHost @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {
    internal fun installRenderer(rendererView: View) {
        checkMainThread()
        if (childCount == 1 && getChildAt(0) === rendererView) return
        removeAllViews()
        addView(
            rendererView,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        )
    }

    internal fun uninstallRenderer(rendererView: View) {
        checkMainThread()
        if (rendererView.parent === this) removeView(rendererView)
    }

    private fun checkMainThread() {
        check(android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
            "FloatingPetRenderHost must be updated on the main thread"
        }
    }
}
