package com.chen.memorizewords.feature.user.ui.profile.phone

import android.os.Bundle
import androidx.lifecycle.ViewModelProvider
import com.chen.memorizewords.core.ui.fragment.BaseFragment
import com.chen.memorizewords.feature.user.databinding.ModuleUserFragmentBindPhoneBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class BindPhoneFragment :
    BaseFragment<BindPhoneViewModel, ModuleUserFragmentBindPhoneBinding>() {

    override val viewModel: BindPhoneViewModel by lazy {
        ViewModelProvider(this)[BindPhoneViewModel::class.java]
    }

    override fun initView(savedInstanceState: Bundle?) {
        databind.viewModel = viewModel
        databind.lifecycleOwner = viewLifecycleOwner
    }
}
