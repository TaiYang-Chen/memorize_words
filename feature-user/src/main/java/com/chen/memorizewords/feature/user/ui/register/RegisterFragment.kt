package com.chen.memorizewords.feature.user.ui.register

import android.os.Bundle
import android.text.InputType
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.core.view.isVisible
import androidx.navigation.fragment.findNavController
import com.chen.memorizewords.core.ui.fragment.BaseFragment
import com.chen.memorizewords.core.ui.vm.UiEvent
import com.chen.memorizewords.feature.user.R
import com.chen.memorizewords.feature.user.databinding.ModuleUserFragmentRegisterBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class RegisterFragment : BaseFragment<RegisterViewModel, ModuleUserFragmentRegisterBinding>() {

    override val viewModel: RegisterViewModel by lazy {
        ViewModelProvider(this)[RegisterViewModel::class.java]
    }

    private var isPasswordVisible = false

    override fun initView(savedInstanceState: Bundle?) {
        databind.viewModel = viewModel
        databind.lifecycleOwner = viewLifecycleOwner
        setupPasswordToggle()
        observeRegisterState()
    }

    private fun observeRegisterState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    databind.tvSystemMessage.isVisible = !state.systemMessage.isNullOrBlank()
                    databind.tvSystemMessage.text = state.systemMessage.orEmpty()
                    databind.btnLogin.isEnabled = !state.submitting
                    databind.btnLogin.alpha = if (state.submitting) 0.6f else 1f
                    val verifiedAlpha = if (state.emphasizeVerifiedRegistration) 1f else 0.82f
                    databind.tvPhoneCodeRegister.alpha = verifiedAlpha
                    databind.tvEmailCodeRegister.alpha = verifiedAlpha
                }
            }
        }
    }

    override fun onNavigationRoute(event: UiEvent.Navigation.Route) {
        when (event.target) {
            RegisterViewModel.Route.ToPhoneCodeRegister ->
                findNavController().navigate(R.id.action_registerFragment_to_phoneCodeRegisterFragment)

            RegisterViewModel.Route.ToEmailCodeRegister ->
                findNavController().navigate(R.id.action_registerFragment_to_emailCodeRegisterFragment)
        }
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

}
