package com.chen.memorizewords.feature.user.ui.register

import android.os.Bundle
import android.text.InputType
import androidx.lifecycle.ViewModelProvider
import com.chen.memorizewords.core.ui.fragment.BaseFragment
import com.chen.memorizewords.feature.user.R
import com.chen.memorizewords.feature.user.databinding.ModuleUserFragmentRegisterBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class RegisterFragment : BaseFragment<RegisterViewModel, ModuleUserFragmentRegisterBinding>() {

    override val viewModel: RegisterViewModel by lazy {
        ViewModelProvider(this)[RegisterViewModel::class.java]
    }

    private var isPasswordVisible = false

    override fun initView(savedInstanceState: Bundle?) {
        databind.viewModel = viewModel
        setupPasswordToggle()
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
