package com.chen.memorizewords.feature.user.ui.login

import android.os.Bundle
import android.text.InputType
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import com.chen.memorizewords.core.ui.fragment.BaseFragment
import com.chen.memorizewords.core.ui.vm.UiEvent
import com.chen.memorizewords.feature.user.R
import com.chen.memorizewords.feature.user.databinding.ModuleUserFragmentLoginBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class LoginFragment : BaseFragment<LoginViewModel, ModuleUserFragmentLoginBinding>() {

    override val viewModel: LoginViewModel by lazy {
        ViewModelProvider(this)[LoginViewModel::class.java]
    }

    private var isPasswordVisible = false

    override fun initView(savedInstanceState: Bundle?) {
        databind.viewModel = viewModel
        setupPasswordToggle()
        setupNavigationClicks()
    }

    override fun onNavigationRoute(event: UiEvent.Navigation.Route) {
        when (event.target) {
            LoginViewModel.Route.ToRegister -> {
                findNavController().navigate(R.id.action_loginFragment_to_registerFragment)
            }

            LoginViewModel.Route.ToWeChatOneTapLogin -> {
                findNavController().navigate(R.id.action_loginFragment_to_wechatOneTapLoginFragment)
            }

            LoginViewModel.Route.ToQQOneTapLogin -> {
                findNavController().navigate(R.id.action_loginFragment_to_qqOneTapLoginFragment)
            }

            LoginViewModel.Route.ToSmsCodeLogin -> {
                findNavController().navigate(R.id.action_loginFragment_to_smsCodeLoginFragment)
            }
        }
    }

    private fun setupPasswordToggle() {
        updatePasswordInputType()
        databind.ivEye.setOnClickListener {
            isPasswordVisible = !isPasswordVisible
            updatePasswordInputType()
        }
    }

    private fun setupNavigationClicks() {
        databind.tvCodeLogin.setOnClickListener {
            viewModel.navigateToSmsCodeLogin()
        }
        databind.ivWechatLogin.setOnClickListener {
            viewModel.navigateToWeChatOneTapLogin()
        }
        databind.ivQqLogin.setOnClickListener {
            viewModel.navigateToQQOneTapLogin()
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
