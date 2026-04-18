package com.chen.memorizewords.feature.user.ui.profile.password

import android.content.Intent
import android.os.Bundle
import androidx.lifecycle.ViewModelProvider
import com.chen.memorizewords.core.navigation.AuthEntry
import com.chen.memorizewords.core.ui.fragment.BaseFragment
import com.chen.memorizewords.core.ui.vm.UiEvent
import com.chen.memorizewords.feature.user.databinding.ModuleUserFragmentChangePasswordBinding
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class ChangePasswordFragment :
    BaseFragment<ChangePasswordViewModel, ModuleUserFragmentChangePasswordBinding>() {

    override val viewModel: ChangePasswordViewModel by lazy {
        ViewModelProvider(this)[ChangePasswordViewModel::class.java]
    }

    @Inject
    lateinit var authEntry: AuthEntry

    override fun initView(savedInstanceState: Bundle?) {
        databind.viewModel = viewModel
    }

    override fun onNavigationRoute(event: UiEvent.Navigation.Route) {
        when (event.target) {
            ChangePasswordViewModel.Route.ToLogin -> {
                startActivity(
                    authEntry.createAuthIntent(requireContext()).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    }
                )
                requireActivity().finish()
            }
        }
    }
}
