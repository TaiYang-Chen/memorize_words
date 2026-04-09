package com.chen.memorizewords.core.ui.dialog.prefabricated

import android.os.Bundle
import androidx.lifecycle.ViewModelProvider
import com.chen.memorizewords.core.ui.databinding.DialogEditBinding
import com.chen.memorizewords.core.ui.dialog.BaseDialogFragment
import com.chen.memorizewords.core.ui.vm.BaseViewModel
import com.chen.memorizewords.core.ui.vm.UiEvent

class ShowConfirmEditDialog(
    private val data: UiEvent.Dialog.ConfirmEdit,
    private val onConfirm: ((String) -> Unit)? = null,
    private val onCancel: (() -> Unit)? = null
) :
    BaseDialogFragment<BaseViewModel, DialogEditBinding>() {

    override val viewModel: BaseViewModel by lazy {
        ViewModelProvider(this)[BaseViewModel::class.java]
    }

    override fun initView(savedInstanceState: Bundle?) {
        databind.title.text = data.title
        databind.content.setText(data.content)
        databind.content.setHint(data.hint)
        databind.confirm.setOnClickListener {
            onConfirm?.invoke(databind.content.text.toString())
            dismiss()
        }
        databind.cancel.setOnClickListener {
            onCancel?.invoke()
            dismiss()
        }
    }
}
