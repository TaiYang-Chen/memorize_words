package com.chen.memorizewords.feature.user.ui.login.wechat

import android.os.Bundle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.chen.memorizewords.core.ui.fragment.BaseFragment
import com.chen.memorizewords.feature.user.R
import com.chen.memorizewords.feature.user.auth.social.WeChatAuthProvider
import com.chen.memorizewords.feature.user.databinding.ModuleUserFragmentWechatOneTapLoginBinding
import dagger.hilt.android.AndroidEntryPoint
import jakarta.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class WeChatOneTapLoginFragment :
    BaseFragment<WeChatOneTapLoginViewModel, ModuleUserFragmentWechatOneTapLoginBinding>() {

    override val viewModel: WeChatOneTapLoginViewModel by lazy {
        ViewModelProvider(this)[WeChatOneTapLoginViewModel::class.java]
    }

    @Inject
    lateinit var wechatAuthProvider: WeChatAuthProvider

    override fun initView(savedInstanceState: Bundle?) {
        databind.viewModel = viewModel
        databind.btnWechatAuthorize.setOnClickListener {
            startWechatAuth()
        }
    }

    private fun startWechatAuth() {
        lifecycleScope.launch {
            wechatAuthProvider.requestAuth(requireActivity()).onSuccess { credential ->
                viewModel.loginByWechat(
                    oauthCode = credential.oauthCode,
                    state = credential.state
                )
            }.onFailure { failure ->
                viewModel.showToast(
                    failure.message ?: getString(R.string.module_user_wechat_login_failed)
                )
            }
        }
    }
}
