package com.chen.memorizewords.feature.user.ui.register

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.InputType
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.chen.memorizewords.core.ui.fragment.BaseFragment
import com.chen.memorizewords.feature.user.R
import com.chen.memorizewords.feature.user.auth.fusion.FusionPhoneAuthProvider
import com.chen.memorizewords.feature.user.databinding.ModuleUserFragmentRegisterBinding
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class RegisterFragment : BaseFragment<RegisterViewModel, ModuleUserFragmentRegisterBinding>() {

    override val viewModel: RegisterViewModel by lazy {
        ViewModelProvider(this)[RegisterViewModel::class.java]
    }

    private var isPasswordVisible = false

    @Inject
    lateinit var fusionPhoneAuthProvider: FusionPhoneAuthProvider

    private var pendingPhone: String? = null

    private val phoneStatePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            pendingPhone?.let(::startFusionPhoneRegister)
        } else {
            viewModel.showToast(getString(R.string.module_user_bind_phone_auth_failed))
        }
    }

    override fun initView(savedInstanceState: Bundle?) {
        databind.viewModel = viewModel
        databind.lifecycleOwner = viewLifecycleOwner
        setupPasswordToggle()
        databind.btnLogin.setOnClickListener {
            ensurePhoneStatePermissionThenStart()
        }
    }

    override fun onDestroyView() {
        fusionPhoneAuthProvider.destroy()
        super.onDestroyView()
    }

    private fun setupPasswordToggle() {
        updatePasswordInputType()
        databind.ivEye.setOnClickListener {
            isPasswordVisible = !isPasswordVisible
            updatePasswordInputType()
        }
    }

    private fun updatePasswordInputType() {
        val passwordEditText = databind.etPassword
        val cursorPosition = passwordEditText.selectionEnd.coerceAtLeast(0)
        passwordEditText.inputType = if (isPasswordVisible) {
            databind.ivEye.setImageResource(android.R.drawable.ic_menu_view)
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
        } else {
            databind.ivEye.setImageResource(R.drawable.module_user_login_ic_pass_invisible)
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        val textLength = passwordEditText.text?.length ?: 0
        passwordEditText.setSelection(cursorPosition.coerceAtMost(textLength))
    }

    private fun ensurePhoneStatePermissionThenStart() {
        val phone = viewModel.getValidatedPhone() ?: return
        pendingPhone = phone
        if (ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.READ_PHONE_STATE
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            startFusionPhoneRegister(phone)
        } else {
            phoneStatePermissionLauncher.launch(Manifest.permission.READ_PHONE_STATE)
        }
    }

    private fun startFusionPhoneRegister(phone: String) {
        lifecycleScope.launch {
            fusionPhoneAuthProvider.requestVerifyToken(
                activity = requireActivity(),
                templateId = REGISTER_PHONE_SCENE_ID,
                phoneForVerification = phone
            ).onSuccess { verifyToken ->
                viewModel.registerByFusionVerifyToken(verifyToken)
            }.onFailure { failure ->
                viewModel.showToast(
                    failure.message ?: getString(R.string.module_user_bind_phone_auth_failed)
                )
            }
        }
    }

    private companion object {
        const val REGISTER_PHONE_SCENE_ID = "100004"
    }
}
