package com.chen.memorizewords.feature.user.ui.profile.phone

import android.os.Bundle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.chen.memorizewords.core.ui.fragment.BaseFragment
import com.chen.memorizewords.feature.user.R
import com.chen.memorizewords.feature.user.auth.fusion.FusionPhoneAuthProvider
import com.chen.memorizewords.feature.user.databinding.ModuleUserFragmentBindPhoneBinding
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class BindPhoneFragment :
    BaseFragment<BindPhoneViewModel, ModuleUserFragmentBindPhoneBinding>() {

    override val viewModel: BindPhoneViewModel by lazy {
        ViewModelProvider(this)[BindPhoneViewModel::class.java]
    }

    @Inject
    lateinit var fusionPhoneAuthProvider: FusionPhoneAuthProvider

    override fun initView(savedInstanceState: Bundle?) {
        databind.viewModel = viewModel
        databind.lifecycleOwner = viewLifecycleOwner
        databind.btnSendCode.setOnClickListener {
            sendFusionSmsCode()
        }
        databind.btnConfirmBind.setOnClickListener {
            verifyFusionSmsCodeAndBind()
        }
    }

    override fun onDestroyView() {
        fusionPhoneAuthProvider.destroy()
        super.onDestroyView()
    }

    private fun sendFusionSmsCode() {
        val phone = viewModel.getValidatedPhoneForSendingCode() ?: return
        viewModel.showSendingCode()
        lifecycleScope.launch {
            fusionPhoneAuthProvider.sendSmsCodeInPlace(
                context = requireContext(),
                templateId = BIND_PHONE_SMS_SCENE_ID,
                phone = phone,
                smsNodeId = BIND_PHONE_SMS_NODE_ID
            ).onSuccess { verifyToken ->
                viewModel.markFusionSmsSent(phone)
            }.onFailure { failure ->
                viewModel.restoreSendCodeButton()
                viewModel.showToast(
                    failure.message ?: getString(R.string.module_user_bind_phone_auth_failed)
                )
            }
        }
    }

    private fun verifyFusionSmsCodeAndBind() {
        val phone = viewModel.getValidatedPhone() ?: return
        val smsCode = viewModel.getValidatedSmsCode() ?: return
        fusionPhoneAuthProvider.verifySmsCodeInPlace(
            context = requireContext(),
            templateId = BIND_PHONE_SMS_SCENE_ID,
            phone = phone,
            code = smsCode,
            smsNodeId = BIND_PHONE_SMS_NODE_ID
        ).onSuccess { verifyToken ->
            viewModel.bindFusionVerifiedPhone(phone, verifyToken)
        }.onFailure { failure ->
            viewModel.showToast(
                failure.message ?: getString(R.string.module_user_bind_phone_auth_failed)
            )
        }
    }

    private companion object {
        const val BIND_PHONE_SMS_SCENE_ID = "100004"
        const val BIND_PHONE_SMS_NODE_ID = "300000100004003"
    }
}
