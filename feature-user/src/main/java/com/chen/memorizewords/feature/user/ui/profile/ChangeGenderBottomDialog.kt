package com.chen.memorizewords.feature.user.ui.profile

import android.os.Bundle
import com.aigestudio.wheelpicker.WheelPicker
import androidx.lifecycle.ViewModelProvider
import com.chen.memorizewords.core.ui.bottomsheetdialogfragment.BaseBottomSheetDialogFragment
import com.chen.memorizewords.core.ui.databinding.BottomDialogWarnBinding
import com.chen.memorizewords.core.ui.vm.BaseViewModel

import com.chen.memorizewords.core.ui.vm.UiEvent
import com.chen.memorizewords.feature.user.databinding.ModuleUserDialogChangeGenderBinding

class ChangeGenderBottomDialog(val confirm: (String) -> Unit) :
    BaseBottomSheetDialogFragment<BaseViewModel, ModuleUserDialogChangeGenderBinding>() {

    override val viewModel: BaseViewModel by lazy {
        ViewModelProvider(this)[BaseViewModel::class.java]
    }

    override fun initView(savedInstanceState: Bundle?) {
        val listOf = listOf("男", "女")
        databind.wheelPicker.data = listOf
        databind.confirm.setOnClickListener {
            confirm(listOf[databind.wheelPicker.currentItemPosition])
            dismiss()
        }
        databind.cancel.setOnClickListener {
            dismiss()
        }
    }
}
