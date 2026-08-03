package com.chen.memorizewords.core.sprite

import android.content.Context
import android.util.AttributeSet
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
}
