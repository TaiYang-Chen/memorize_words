package com.chen.memorizewords.feature.user.ui.login.email

import android.os.Bundle
import androidx.lifecycle.ViewModelProvider
import com.chen.memorizewords.core.ui.fragment.BaseFragment
import com.chen.memorizewords.core.ui.vm.UiEvent
import com.chen.memorizewords.feature.user.databinding.ModuleUserFragmentEmailCodeLoginBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class EmailCodeLoginFragment :
    BaseFragment<EmailCodeLoginViewModel, ModuleUserFragmentEmailCodeLoginBinding>() {

    override val viewModel: EmailCodeLoginViewModel by lazy {
        ViewModelProvider(this)[EmailCodeLoginViewModel::class.java]
    }

    override fun initView(savedInstanceState: Bundle?) {
        databind.viewModel = viewModel
        databind.lifecycleOwner = viewLifecycleOwner
    }

    override fun onConfirmDialog(event: UiEvent.Dialog.Confirm) {
        if (event.action == EmailCodeLoginViewModel.ACTION_CANCEL_DELETION_LOGIN) {
            viewModel.confirmCancelDeletionAndLogin()
            return
        }
        super.onConfirmDialog(event)
    }
}
