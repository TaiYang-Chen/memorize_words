package com.chen.memorizewords.feature.user.ui.register.phone

import android.os.Bundle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.chen.memorizewords.core.ui.fragment.BaseFragment
import com.chen.memorizewords.feature.user.R
import com.chen.memorizewords.feature.user.auth.fusion.FusionPhoneAuthProvider
import com.chen.memorizewords.feature.user.databinding.ModuleUserFragmentPhoneCodeRegisterBinding
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class PhoneCodeRegisterFragment :
    BaseFragment<PhoneCodeRegisterViewModel, ModuleUserFragmentPhoneCodeRegisterBinding>() {

    override val viewModel: PhoneCodeRegisterViewModel by lazy {
        ViewModelProvider(this)[PhoneCodeRegisterViewModel::class.java]
    }

    @Inject
    lateinit var fusionPhoneAuthProvider: FusionPhoneAuthProvider

    override fun initView(savedInstanceState: Bundle?) {
        databind.viewModel = viewModel
        databind.lifecycleOwner = viewLifecycleOwner
        databind.btnSendCode.setOnClickListener {
            sendFusionSmsCode()
        }
        databind.btnRegister.setOnClickListener {
            verifyFusionSmsCodeAndRegister()
        }
    }

    override fun onDestroyView() {
        fusionPhoneAuthProvider.destroy()
        super.onDestroyView()
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

    private fun verifyFusionSmsCodeAndRegister() {
        val phone = viewModel.getValidatedPhone() ?: return
        val smsCode = viewModel.getValidatedSmsCode() ?: return
        fusionPhoneAuthProvider.verifySmsCodeInPlace(
            context = requireContext(),
            templateId = PHONE_CODE_SCENE_ID,
            phone = phone,
            code = smsCode,
            smsNodeId = PHONE_CODE_SMS_NODE_ID
        ).onSuccess { verifyToken ->
            viewModel.registerWithVerifiedPhone(phone, verifyToken)
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
