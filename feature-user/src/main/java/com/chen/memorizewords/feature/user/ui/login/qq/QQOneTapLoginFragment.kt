package com.chen.memorizewords.feature.user.ui.login.qq

import android.os.Bundle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.chen.memorizewords.core.ui.fragment.BaseFragment
import com.chen.memorizewords.feature.user.R
import com.chen.memorizewords.feature.user.auth.social.QQAuthProvider
import com.chen.memorizewords.feature.user.databinding.ModuleUserFragmentQqOneTapLoginBinding
import dagger.hilt.android.AndroidEntryPoint
import jakarta.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class QQOneTapLoginFragment :
    BaseFragment<QQOneTapLoginViewModel, ModuleUserFragmentQqOneTapLoginBinding>() {

    override val viewModel: QQOneTapLoginViewModel by lazy {
        ViewModelProvider(this)[QQOneTapLoginViewModel::class.java]
    }

    @Inject
    lateinit var qqAuthProvider: QQAuthProvider

    override fun initView(savedInstanceState: Bundle?) {
        databind.viewModel = viewModel
        databind.btnQqAuthorize.setOnClickListener {
            startQqAuth()
        }
    }

    private fun startQqAuth() {
        lifecycleScope.launch {
            qqAuthProvider.requestAuth(requireActivity()).onSuccess { credential ->
                viewModel.loginByQq(
                    oauthCode = credential.oauthCode,
                    state = credential.state
                )
            }.onFailure { failure ->
                viewModel.showToast(
                    failure.message ?: getString(R.string.module_user_qq_login_failed)
                )
            }
        }
    }
}
