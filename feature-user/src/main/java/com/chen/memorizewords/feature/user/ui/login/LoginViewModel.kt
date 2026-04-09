package com.chen.memorizewords.feature.user.ui.login

import com.chen.memorizewords.core.common.resource.ResourceProvider
import com.chen.memorizewords.core.ui.vm.BaseViewModel
import com.chen.memorizewords.domain.usecase.user.LoginError
import com.chen.memorizewords.domain.usecase.user.LoginUseCase
import com.chen.memorizewords.feature.user.R
import com.chen.memorizewords.feature.user.ui.resolveAuthFailureMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import jakarta.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val loginUseCase: LoginUseCase,
    private val resourceProvider: ResourceProvider
) : BaseViewModel() {

    sealed interface Route {
        data object ToRegister : Route
        data object ToWeChatOneTapLogin : Route
        data object ToQQOneTapLogin : Route
        data object ToSmsCodeLogin : Route
    }

    val phoneNumber = MutableStateFlow("")
    val password = MutableStateFlow("")

    fun login() {
        launchWithLoading("登录中...") {
            loginUseCase(phoneNumber.value, password.value).onSuccess {
                showToast(resourceProvider.getString(R.string.module_user_login_success))
                finish()
            }.onFailure { failure ->
                when (failure) {
                    is LoginError.EmptyPhone ->
                        showToast(resourceProvider.getString(R.string.module_user_login_phone_required))

                    is LoginError.EmptyPassword ->
                        showToast(resourceProvider.getString(R.string.module_user_login_password_required))

                    else -> showToast(
                        resolveAuthFailureMessage(
                            failure,
                            resourceProvider.getString(R.string.module_user_login_failed)
                        )
                    )
                }
            }
        }
    }

    fun navigateToRegister() {
        navigateRoute(Route.ToRegister)
    }

    fun navigateToWeChatOneTapLogin() {
        navigateRoute(Route.ToWeChatOneTapLogin)
    }

    fun navigateToQQOneTapLogin() {
        navigateRoute(Route.ToQQOneTapLogin)
    }

    fun navigateToSmsCodeLogin() {
        navigateRoute(Route.ToSmsCodeLogin)
    }
}
