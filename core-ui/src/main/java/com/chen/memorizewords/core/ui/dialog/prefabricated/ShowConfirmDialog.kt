package com.chen.memorizewords.core.ui.dialog.prefabricated

import android.os.Bundle
import androidx.lifecycle.ViewModelProvider
import com.chen.memorizewords.core.ui.databinding.DialogWarnBinding
import com.chen.memorizewords.core.ui.dialog.BaseDialogFragment
import com.chen.memorizewords.core.ui.vm.BaseViewModel
import com.chen.memorizewords.core.ui.vm.UiEvent

class ShowConfirmDialog(
    private val data: UiEvent.Dialog.Confirm,
    private val onConfirm: (() -> Unit)? = null,
    private val onCancel: (() -> Unit)? = null
) :
    BaseDialogFragment<BaseViewModel, DialogWarnBinding>() {

    override val viewModel: BaseViewModel by lazy {
        ViewModelProvider(this)[BaseViewModel::class.java]
    }

    override fun initView(savedInstanceState: Bundle?) {
        databind.title.text = data.title
        databind.message.text = data.message
        databind.confirm.text = data.confirmText
        databind.cancel.text = data.cancelText
        databind.confirm.setOnClickListener {
            onConfirm?.invoke()
            dismiss()
        }
        databind.cancel.setOnClickListener {
            onCancel?.invoke()
            dismiss()
        }
    }
}
