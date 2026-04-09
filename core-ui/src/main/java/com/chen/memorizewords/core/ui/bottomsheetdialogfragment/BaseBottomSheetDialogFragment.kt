package com.chen.memorizewords.core.ui.bottomsheetdialogfragment

import android.graphics.Color
import android.view.View
import androidx.databinding.ViewDataBinding
import com.chen.memorizewords.core.ui.vm.BaseViewModel

abstract class BaseBottomSheetDialogFragment<VM : BaseViewModel, DB : ViewDataBinding> : BaseVmDbBottomSheetDialogFragment<VM, DB>() {

    override fun createObserver() {}

    override fun initData() {}

    override fun onStart() {
        super.onStart()

        val bottomSheet = dialog
            ?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)

        bottomSheet?.setBackgroundColor(Color.TRANSPARENT)
    }
}