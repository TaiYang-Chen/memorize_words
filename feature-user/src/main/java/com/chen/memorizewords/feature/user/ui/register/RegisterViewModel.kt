package com.chen.memorizewords.feature.user.ui.register

import com.chen.memorizewords.core.common.resource.ResourceProvider
import com.chen.memorizewords.core.ui.vm.BaseViewModel
import com.chen.memorizewords.domain.account.usecase.user.FusionPhoneRegisterUseCase
import com.chen.memorizewords.domain.account.usecase.user.LoginDataSyncError
import com.chen.memorizewords.domain.account.usecase.user.LoginError
import com.chen.memorizewords.feature.user.R
import com.chen.memorizewords.feature.user.ui.resolveAuthFailureMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import jakarta.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow

@HiltViewModel
class RegisterViewModel @Inject constructor(
    private val fusionPhoneRegisterUseCase: FusionPhoneRegisterUseCase,
    private val resourceProvider: ResourceProvider
) : BaseViewModel() {

    val phone = MutableStateFlow("")
    val password = MutableStateFlow("")

    fun getValidatedPhone(): String? {
        val targetPhone = phone.value.trim()
        if (targetPhone.isBlank()) {
            showToast(resourceProvider.getString(R.string.module_user_login_phone_required))
            return null
        }
        if (!PHONE_PATTERN.matches(targetPhone)) {
            showToast(resourceProvider.getString(R.string.module_user_bind_phone_phone_invalid))
            return null
        }
        return targetPhone
    }

    fun registerByFusionVerifyToken(verifyToken: String) {
        launchWithLoading("\u6CE8\u518C\u4E2D...") {
            fusionPhoneRegisterUseCase(verifyToken, password.value).onSuccess {
                showToast("\u6CE8\u518C\u6210\u529F")
                finish()
            }.onFailure { failure ->
                when (failure) {
                    is LoginDataSyncError ->
                        showToast(resourceProvider.getString(R.string.module_user_login_sync_failed))

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

    private companion object {
        val PHONE_PATTERN = "^1[3-9]\\d{9}$".toRegex()
    }
}
