package com.chen.memorizewords.core.ui.dialog.loading

import androidx.fragment.app.FragmentManager

class LoadingDialogController(
    private val fragmentManager: FragmentManager
) {

    fun show(message: String) {
        val existing = fragmentManager.findFragmentByTag(TAG) as? LoadingDialogFragment
        if (existing != null) {
            existing.updateMessage(message)
            return
        }
        if (fragmentManager.isStateSaved) return
        LoadingDialogFragment.newInstance(message).show(fragmentManager, TAG)
    }

    fun hide() {
        (fragmentManager.findFragmentByTag(TAG) as? LoadingDialogFragment)
            ?.dismissAllowingStateLoss()
    }

    companion object {
        private const val TAG = "LoadingDialogFragment"
    }
}
