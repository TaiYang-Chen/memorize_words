package com.chen.memorizewords.core.ui.dialog.prefabricated

import android.os.Bundle
import androidx.lifecycle.ViewModelProvider
import com.chen.memorizewords.core.ui.bottomsheetdialogfragment.BaseBottomSheetDialogFragment
import com.chen.memorizewords.core.ui.databinding.BottomDialogWarnBinding
import com.chen.memorizewords.core.ui.vm.BaseViewModel
import com.chen.memorizewords.core.ui.vm.UiEvent

class ShowConfirmBottomDialog(
    private val data: UiEvent.Dialog.ConfirmBottom,
    private val onConfirm: (() -> Unit)? = null,
    private val onCancel: (() -> Unit)? = null
) :
    BaseBottomSheetDialogFragment<BaseViewModel, BottomDialogWarnBinding>() {

    override val viewModel: BaseViewModel by lazy {
        ViewModelProvider(this)[BaseViewModel::class.java]
    }

    override fun initView(savedInstanceState: Bundle?) {
        databind.title.text = data.title
        databind.message.text = data.message
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
