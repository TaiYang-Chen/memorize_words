package com.chen.memorizewords.feature.user.ui.profile.avatar

import android.os.Bundle
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.navArgs
import coil.load
import com.chen.memorizewords.core.ui.fragment.BaseFragment
import com.chen.memorizewords.core.ui.vm.BaseViewModel
import com.chen.memorizewords.feature.user.R
import com.chen.memorizewords.feature.user.databinding.ModuleUserFragmentAvatarPreviewBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class AvatarPreviewFragment :
    BaseFragment<BaseViewModel, ModuleUserFragmentAvatarPreviewBinding>() {

    override val viewModel: BaseViewModel by lazy {
        ViewModelProvider(this)[BaseViewModel::class.java]
    }

    private val args: AvatarPreviewFragmentArgs by navArgs()

    override fun initView(savedInstanceState: Bundle?) {
        databind.viewModel = viewModel
        val source = args.avatarSource
        if (source.isBlank()) {
            databind.ivAvatar.setImageResource(R.drawable.module_user_ic_avatar_placeholder)
            viewModel.showToast(getString(R.string.module_user_profile_no_avatar))
            return
        }
        databind.ivAvatar.load(source) {
            placeholder(R.drawable.module_user_ic_avatar_placeholder)
            error(R.drawable.module_user_ic_avatar_placeholder)
        }
    }
}
