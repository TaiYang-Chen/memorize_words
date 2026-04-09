package com.chen.memorizewords.feature.user.ui.login.wechat

import com.chen.memorizewords.core.common.resource.ResourceProvider
import com.chen.memorizewords.core.ui.vm.BaseViewModel
import com.chen.memorizewords.domain.usecase.user.LoginError
import com.chen.memorizewords.domain.usecase.user.WeChatLoginUseCase
import com.chen.memorizewords.feature.user.R
import com.chen.memorizewords.feature.user.ui.resolveAuthFailureMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import jakarta.inject.Inject

@HiltViewModel
class WeChatOneTapLoginViewModel @Inject constructor(
    private val wechatLoginUseCase: WeChatLoginUseCase,
    private val resourceProvider: ResourceProvider
) : BaseViewModel() {

    fun loginByWechat(oauthCode: String, state: String?) {
        launchWithLoading("\u767B\u5F55\u4E2D...") {
            wechatLoginUseCase(oauthCode, state).onSuccess {
                showToast(resourceProvider.getString(R.string.module_user_login_success))
                finish()
            }.onFailure { failure ->
                when (failure) {
                    is LoginError.EmptyOauthCode ->
                        showToast(resourceProvider.getString(R.string.module_user_wechat_auth_failed))
                    else -> showToast(
                        resolveAuthFailureMessage(
                            failure,
                            resourceProvider.getString(R.string.module_user_wechat_login_failed)
                        )
                    )
                }
            }
        }
    }
}
