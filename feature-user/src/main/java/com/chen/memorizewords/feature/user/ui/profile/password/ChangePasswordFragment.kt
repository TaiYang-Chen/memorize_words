package com.chen.memorizewords.feature.user.ui.profile.password

import android.os.Bundle
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import com.chen.memorizewords.core.ui.fragment.BaseFragment
import com.chen.memorizewords.core.ui.vm.UiEvent
import com.chen.memorizewords.feature.user.R
import com.chen.memorizewords.feature.user.databinding.ModuleUserFragmentChangePasswordBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class ChangePasswordFragment :
    BaseFragment<ChangePasswordViewModel, ModuleUserFragmentChangePasswordBinding>() {

    override val viewModel: ChangePasswordViewModel by lazy {
        ViewModelProvider(this)[ChangePasswordViewModel::class.java]
    }

    override fun initView(savedInstanceState: Bundle?) {
        databind.viewModel = viewModel
    }

    override fun onNavigationRoute(event: UiEvent.Navigation.Route) {
        when (event.target) {
            ChangePasswordViewModel.Route.ToLogin -> {
                findNavController().navigate(R.id.action_changePasswordFragment_to_loginFragment)
            }
        }
    }
}
