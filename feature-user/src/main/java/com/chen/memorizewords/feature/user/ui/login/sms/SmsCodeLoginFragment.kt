package com.chen.memorizewords.feature.user.ui.login.sms

import android.os.Bundle
import androidx.lifecycle.ViewModelProvider
import com.chen.memorizewords.core.ui.fragment.BaseFragment
import com.chen.memorizewords.feature.user.databinding.ModuleUserFragmentSmsCodeLoginBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class SmsCodeLoginFragment : BaseFragment<SmsCodeLoginViewModel, ModuleUserFragmentSmsCodeLoginBinding>() {

    override val viewModel: SmsCodeLoginViewModel by lazy {
        ViewModelProvider(this)[SmsCodeLoginViewModel::class.java]
    }

    override fun initView(savedInstanceState: Bundle?) {
        databind.viewModel = viewModel
        databind.lifecycleOwner = viewLifecycleOwner
    }
}
