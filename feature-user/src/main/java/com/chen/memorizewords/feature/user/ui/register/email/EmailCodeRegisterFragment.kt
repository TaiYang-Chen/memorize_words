package com.chen.memorizewords.feature.user.ui.register.email

import android.os.Bundle
import androidx.lifecycle.ViewModelProvider
import com.chen.memorizewords.core.ui.fragment.BaseFragment
import com.chen.memorizewords.feature.user.databinding.ModuleUserFragmentEmailCodeRegisterBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class EmailCodeRegisterFragment :
    BaseFragment<EmailCodeRegisterViewModel, ModuleUserFragmentEmailCodeRegisterBinding>() {

    override val viewModel: EmailCodeRegisterViewModel by lazy {
        ViewModelProvider(this)[EmailCodeRegisterViewModel::class.java]
    }

    override fun initView(savedInstanceState: Bundle?) {
        databind.viewModel = viewModel
        databind.lifecycleOwner = viewLifecycleOwner
    }
}
