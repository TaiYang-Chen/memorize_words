package com.chen.memorizewords.feature.user.ui.profile.account

import android.content.Intent
import android.os.Bundle
import androidx.lifecycle.ViewModelProvider
import com.chen.memorizewords.core.ui.fragment.BaseFragment
import com.chen.memorizewords.core.ui.vm.UiEffect
import com.chen.memorizewords.feature.user.databinding.ModuleUserFragmentDeleteAccountConfirmBinding
import com.chen.memorizewords.core.navigation.AuthEntry
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class DeleteAccountConfirmFragment :
    BaseFragment<DeleteAccountConfirmViewModel, ModuleUserFragmentDeleteAccountConfirmBinding>() {

    override val viewModel: DeleteAccountConfirmViewModel by lazy {
        ViewModelProvider(this)[DeleteAccountConfirmViewModel::class.java]
    }

    @Inject
    lateinit var authEntry: AuthEntry

    override fun initView(savedInstanceState: Bundle?) {
        databind.viewModel = viewModel
        databind.lifecycleOwner = viewLifecycleOwner
    }

    override fun onUiEffect(effect: UiEffect) {
        when (effect) {
            DeleteAccountConfirmViewModel.Effect.NavigateToAuthClearTask -> {
                startActivity(
                    authEntry.createAuthIntent(requireContext()).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    }
                )
            }
        }
    }
}
