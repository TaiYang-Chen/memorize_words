package com.chen.memorizewords.feature.user.ui.profile.email

import android.os.Bundle
import androidx.lifecycle.ViewModelProvider
import com.chen.memorizewords.core.ui.fragment.BaseFragment
import com.chen.memorizewords.feature.user.databinding.ModuleUserFragmentBindEmailBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class BindEmailFragment :
    BaseFragment<BindEmailViewModel, ModuleUserFragmentBindEmailBinding>() {

    override val viewModel: BindEmailViewModel by lazy {
        ViewModelProvider(this)[BindEmailViewModel::class.java]
    }

    override fun initView(savedInstanceState: Bundle?) {
        databind.viewModel = viewModel
        databind.lifecycleOwner = viewLifecycleOwner
    }
}
