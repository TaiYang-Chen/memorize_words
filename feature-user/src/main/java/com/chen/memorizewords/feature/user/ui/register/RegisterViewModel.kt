package com.chen.memorizewords.feature.user.ui.register

import com.chen.memorizewords.core.common.resource.ResourceProvider
import com.chen.memorizewords.core.ui.vm.BaseViewModel
import com.chen.memorizewords.domain.usecase.user.LoginError
import com.chen.memorizewords.domain.usecase.user.RegisterUseCase
import com.chen.memorizewords.feature.user.R
import com.chen.memorizewords.feature.user.ui.resolveAuthFailureMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import jakarta.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow

@HiltViewModel
class RegisterViewModel @Inject constructor(
    private val registerUseCase: RegisterUseCase,
    private val resourceProvider: ResourceProvider
) : BaseViewModel() {

    val phoneNumber = MutableStateFlow("")
    val password = MutableStateFlow("")

    fun register() {
        launchWithLoading("\u6CE8\u518C\u4E2D...") {
            registerUseCase(phoneNumber.value, password.value).onSuccess {
                showToast("\u6CE8\u518C\u6210\u529F")
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
                            resourceProvider.getString(R.string.module_user_register_failed)
                        )
                    )
                }
            }
        }
    }
}
