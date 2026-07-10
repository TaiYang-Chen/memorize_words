package com.chen.memorizewords.core.ui.dialog.loading

import androidx.fragment.app.FragmentManager

class LoadingDialogController(
    private val fragmentManager: FragmentManager,
    private val tag: String = DEFAULT_TAG
) {

    fun show(message: String, onFirstFrameDrawn: (() -> Unit)? = null): Boolean {
        val existing = fragmentManager.findFragmentByTag(tag) as? LoadingDialogFragment
        if (existing != null) {
            existing.updateMessage(message)
            existing.setOnFirstFrameDrawnListener(onFirstFrameDrawn)
            return true
        }
        if (fragmentManager.isStateSaved) return false
        LoadingDialogFragment.newInstance(message).apply {
            setOnFirstFrameDrawnListener(onFirstFrameDrawn)
        }.show(fragmentManager, tag)
        return true
    }

    fun hide() {
        (fragmentManager.findFragmentByTag(tag) as? LoadingDialogFragment)
            ?.dismissAllowingStateLoss()
    }

    companion object {
        private const val DEFAULT_TAG = "LoadingDialogFragment"
    }
}
