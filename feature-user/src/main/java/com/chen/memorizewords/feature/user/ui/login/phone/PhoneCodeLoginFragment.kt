package com.chen.memorizewords.feature.user.ui.login.phone

import android.os.Bundle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.chen.memorizewords.core.ui.fragment.BaseFragment
import com.chen.memorizewords.core.ui.vm.UiEvent
import com.chen.memorizewords.feature.user.R
import com.chen.memorizewords.feature.user.auth.fusion.FusionPhoneAuthProvider
import com.chen.memorizewords.feature.user.databinding.ModuleUserFragmentPhoneCodeLoginBinding
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class PhoneCodeLoginFragment :
    BaseFragment<PhoneCodeLoginViewModel, ModuleUserFragmentPhoneCodeLoginBinding>() {

    override val viewModel: PhoneCodeLoginViewModel by lazy {
        ViewModelProvider(this)[PhoneCodeLoginViewModel::class.java]
    }

    @Inject
    lateinit var fusionPhoneAuthProvider: FusionPhoneAuthProvider

    override fun initView(savedInstanceState: Bundle?) {
        databind.viewModel = viewModel
        databind.lifecycleOwner = viewLifecycleOwner
        databind.btnSendCode.setOnClickListener {
            sendFusionSmsCode()
        }
        databind.btnLogin.setOnClickListener {
            verifyFusionSmsCodeAndLogin()
        }
    }

    override fun onDestroyView() {
        fusionPhoneAuthProvider.destroy()
        super.onDestroyView()
    }

    override fun onConfirmDialog(event: UiEvent.Dialog.Confirm) {
        if (event.action == PhoneCodeLoginViewModel.ACTION_CANCEL_DELETION_LOGIN) {
            viewModel.confirmCancelDeletionAndLogin()
            return
        }
        super.onConfirmDialog(event)
    }

    private fun sendFusionSmsCode() {
        val phone = viewModel.getValidatedPhone() ?: return
        viewModel.showSendingCode()
        lifecycleScope.launch {
            fusionPhoneAuthProvider.sendSmsCodeInPlace(
                context = requireContext(),
                templateId = PHONE_CODE_SCENE_ID,
                phone = phone,
                smsNodeId = PHONE_CODE_SMS_NODE_ID
            ).onSuccess {
                viewModel.markFusionSmsSent(phone)
            }.onFailure { failure ->
                viewModel.restoreSendCodeButton()
                viewModel.showToast(
                    failure.message ?: getString(R.string.module_user_bind_phone_auth_failed)
                )
            }
        }
    }

    private fun verifyFusionSmsCodeAndLogin() {
        val phone = viewModel.getValidatedPhone() ?: return
        val smsCode = viewModel.getValidatedSmsCode() ?: return
        fusionPhoneAuthProvider.verifySmsCodeInPlace(
            context = requireContext(),
            templateId = PHONE_CODE_SCENE_ID,
            phone = phone,
            code = smsCode,
            smsNodeId = PHONE_CODE_SMS_NODE_ID
        ).onSuccess { verifyToken ->
            viewModel.loginWithVerifiedPhone(phone, verifyToken)
        }.onFailure { failure ->
            viewModel.showToast(
                failure.message ?: getString(R.string.module_user_bind_phone_auth_failed)
            )
        }
    }

    private companion object {
        const val PHONE_CODE_SCENE_ID = "100004"
        const val PHONE_CODE_SMS_NODE_ID = "300000100004003"
    }
}
