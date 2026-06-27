package com.chen.memorizewords.feature.user.ui.login.fusion

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.chen.memorizewords.core.ui.fragment.BaseFragment
import com.chen.memorizewords.core.ui.vm.UiEvent
import com.chen.memorizewords.feature.user.R
import com.chen.memorizewords.feature.user.auth.fusion.FusionPhoneAuthProvider
import com.chen.memorizewords.feature.user.databinding.ModuleUserFragmentFusionPhoneLoginBinding
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class FusionPhoneLoginFragment :
    BaseFragment<FusionPhoneLoginViewModel, ModuleUserFragmentFusionPhoneLoginBinding>() {

    override val viewModel: FusionPhoneLoginViewModel by lazy {
        ViewModelProvider(this)[FusionPhoneLoginViewModel::class.java]
    }

    @Inject
    lateinit var fusionPhoneAuthProvider: FusionPhoneAuthProvider

    private val phoneStatePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startFusionPhoneAuth()
        } else {
            viewModel.showToast(getString(R.string.module_user_fusion_login_failed))
        }
    }

    override fun initView(savedInstanceState: Bundle?) {
        databind.viewModel = viewModel
        databind.btnFusionAuthorize.setOnClickListener {
            ensurePhoneStatePermissionThenStart()
        }
    }

    override fun onDestroyView() {
        fusionPhoneAuthProvider.destroy()
        super.onDestroyView()
    }

    override fun onConfirmDialog(event: UiEvent.Dialog.Confirm) {
        if (event.action == FusionPhoneLoginViewModel.ACTION_CANCEL_DELETION_LOGIN) {
            viewModel.confirmCancelDeletionAndLogin()
            return
        }
        super.onConfirmDialog(event)
    }

    private fun startFusionPhoneAuth() {
        lifecycleScope.launch {
            fusionPhoneAuthProvider.requestVerifyToken(requireActivity()).onSuccess { verifyToken ->
                viewModel.loginByFusionVerifyToken(verifyToken)
            }.onFailure { failure ->
                viewModel.showToast(
                    failure.message ?: getString(R.string.module_user_fusion_login_failed)
                )
            }
        }
    }

    private fun ensurePhoneStatePermissionThenStart() {
        if (ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.READ_PHONE_STATE
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            startFusionPhoneAuth()
        } else {
            phoneStatePermissionLauncher.launch(Manifest.permission.READ_PHONE_STATE)
        }
    }
}
