package com.chen.memorizewords.feature.user.ui.login.qq

import com.chen.memorizewords.core.common.resource.ResourceProvider
import com.chen.memorizewords.core.ui.vm.BaseViewModel
import com.chen.memorizewords.domain.usecase.user.LoginError
import com.chen.memorizewords.domain.usecase.user.QQLoginUseCase
import com.chen.memorizewords.feature.user.R
import com.chen.memorizewords.feature.user.ui.resolveAuthFailureMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import jakarta.inject.Inject

@HiltViewModel
class QQOneTapLoginViewModel @Inject constructor(
    private val qqLoginUseCase: QQLoginUseCase,
    private val resourceProvider: ResourceProvider
) : BaseViewModel() {

    fun loginByQq(oauthCode: String, state: String?) {
        launchWithLoading("\u767B\u5F55\u4E2D...") {
            qqLoginUseCase(oauthCode, state).onSuccess {
                showToast(resourceProvider.getString(R.string.module_user_login_success))
                finish()
            }.onFailure { failure ->
                when (failure) {
                    is LoginError.EmptyOauthCode ->
                        showToast(resourceProvider.getString(R.string.module_user_qq_auth_failed))
                    else -> showToast(
                        resolveAuthFailureMessage(
                            failure,
                            resourceProvider.getString(R.string.module_user_qq_login_failed)
                        )
                    )
                }
            }
        }
    }
}
