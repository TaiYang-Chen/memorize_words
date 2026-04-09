package com.chen.memorizewords.feature.user.ui.profile.avatar

import android.os.Bundle
import androidx.lifecycle.ViewModelProvider
import com.chen.memorizewords.core.ui.bottomsheetdialogfragment.BaseBottomSheetDialogFragment
import com.chen.memorizewords.core.ui.vm.BaseViewModel
import com.chen.memorizewords.feature.user.databinding.ModuleUserDialogAvatarActionBinding

class AvatarActionBottomSheetDialog(
    private val onViewAvatar: () -> Unit,
    private val onPickFromGallery: () -> Unit,
    private val onTakePhoto: () -> Unit
) : BaseBottomSheetDialogFragment<BaseViewModel, ModuleUserDialogAvatarActionBinding>() {

    override val viewModel: BaseViewModel by lazy {
        ViewModelProvider(this)[BaseViewModel::class.java]
    }

    override fun initView(savedInstanceState: Bundle?) {
        databind.tvViewAvatar.setOnClickListener {
            dismiss()
            onViewAvatar()
        }
        databind.tvPickFromGallery.setOnClickListener {
            dismiss()
            onPickFromGallery()
        }
        databind.tvTakePhoto.setOnClickListener {
            dismiss()
            onTakePhoto()
        }
        databind.tvCancel.setOnClickListener {
            dismiss()
        }
    }
}
