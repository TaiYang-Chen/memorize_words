package com.chen.memorizewords.core.ui.dialog

import android.R.attr.height
import android.R.attr.width
import android.view.ViewGroup
import androidx.databinding.ViewDataBinding
import com.chen.memorizewords.core.ui.vm.BaseViewModel

abstract class BaseDialogFragment<VM : BaseViewModel, DB : ViewDataBinding> :
    BaseVmDbDialogFragment<VM, DB>() {

    override fun createObserver() {}

    override fun initData() {}

    override fun onStart() {
        super.onStart()

        dialog?.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }
}