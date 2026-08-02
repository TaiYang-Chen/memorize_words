package com.chen.memorizewords.core.ui.dialog

import androidx.annotation.LayoutRes
import androidx.fragment.app.DialogFragment
import com.chen.memorizewords.core.ui.insets.configurePhoneDialogForIme

/**
 * Shared host for regular phone dialogs.
 *
 * The platform dialog decor remains responsible for status/navigation-bar fitting; this class only
 * standardizes resize behavior when the IME is shown.
 */
open class PhoneDialogFragment(
    @LayoutRes contentLayoutId: Int = 0
) : DialogFragment(contentLayoutId) {

    override fun onStart() {
        super.onStart()
        configurePhoneDialogForIme()
    }
}
