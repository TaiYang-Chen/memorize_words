package com.chen.memorizewords.core.ui.dialog.prefabricated

import android.os.Bundle
import android.view.View
import androidx.lifecycle.ViewModelProvider
import com.chen.memorizewords.core.ui.databinding.DialogSingleConfirmBinding
import com.chen.memorizewords.core.ui.dialog.BaseDialogFragment
import com.chen.memorizewords.core.ui.vm.BaseViewModel
import com.chen.memorizewords.core.ui.vm.UiEvent

class ShowSingleConfirmDialog(
    private val data: UiEvent.Dialog.SingleConfirm,
    private val onConfirm: (() -> Unit)? = null
) : BaseDialogFragment<BaseViewModel, DialogSingleConfirmBinding>() {

    override val viewModel: BaseViewModel by lazy {
        ViewModelProvider(this)[BaseViewModel::class.java]
    }

    override fun initView(savedInstanceState: Bundle?) {
        databind.title.text = data.title
        databind.title.visibility = if (data.title.isBlank()) View.GONE else View.VISIBLE
        databind.message.text = data.message
        databind.confirm.text = data.confirmText
        databind.confirm.setOnClickListener {
            onConfirm?.invoke()
            dismiss()
        }
    }
}
