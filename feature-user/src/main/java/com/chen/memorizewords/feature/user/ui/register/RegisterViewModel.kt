package com.chen.memorizewords.feature.user.ui.register

import com.chen.memorizewords.core.common.resource.ResourceProvider
import com.chen.memorizewords.core.ui.vm.BaseViewModel
import com.chen.memorizewords.domain.account.usecase.user.LoginDataSyncError
import com.chen.memorizewords.domain.account.usecase.user.LoginError
import com.chen.memorizewords.domain.account.usecase.user.RegisterUseCase
import com.chen.memorizewords.feature.user.R
import com.chen.memorizewords.feature.user.ui.resolveAuthFailureMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import jakarta.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@HiltViewModel
class RegisterViewModel @Inject constructor(
    private val registerUseCase: RegisterUseCase,
    private val resourceProvider: ResourceProvider
) : BaseViewModel() {

    data class RegisterUiState(
        val systemMessage: String? = null,
        val submitting: Boolean = false,
        val emphasizeVerifiedRegistration: Boolean = false
    )

    sealed interface Route {
        data object ToPhoneCodeRegister : Route
        data object ToEmailCodeRegister : Route
    }

    val account = MutableStateFlow("")
    val password = MutableStateFlow("")
    private val _uiState = MutableStateFlow(RegisterUiState())
    val uiState: StateFlow<RegisterUiState> = _uiState.asStateFlow()

    fun register() {
        if (_uiState.value.submitting) return
        _uiState.value = _uiState.value.copy(systemMessage = null, submitting = true)
        launchWithLoading(resourceProvider.getString(R.string.module_user_register_loading)) {
            registerUseCase(account.value, password.value).onSuccess {
                _uiState.value = RegisterUiState()
                showToast(resourceProvider.getString(R.string.module_user_register_success))
                finish()
            }.onFailure { failure ->
                _uiState.value = when (failure) {
                    is LoginError.RegistrationRateLimited -> RegisterUiState(
                        systemMessage = registrationRateLimitMessage(failure.retryAfterSeconds)
                    )
                    is LoginError.RegistrationVerificationRequired -> RegisterUiState(
                        systemMessage = resourceProvider.getString(R.string.module_user_register_verification_required),
                        emphasizeVerifiedRegistration = true
                    )
                    is LoginError.RegistrationBusy -> RegisterUiState(
                        systemMessage = resourceProvider.getString(R.string.module_user_register_service_busy)
                    )
                    else -> RegisterUiState()
                }
                when (failure) {
                    is LoginError.RegistrationRateLimited,
                    is LoginError.RegistrationVerificationRequired,
                    is LoginError.RegistrationBusy -> Unit

                    is LoginDataSyncError ->
                        showToast(resourceProvider.getString(R.string.module_user_login_sync_failed))

                    is LoginError.EmptyAccount ->
                        showToast(resourceProvider.getString(R.string.module_user_register_account_required))

                    is LoginError.InvalidAccount ->
                        showToast(resourceProvider.getString(R.string.module_user_register_account_invalid))

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
            _uiState.value = _uiState.value.copy(submitting = false)
        }
    }

    fun navigateToPhoneCodeRegister() {
        navigateRoute(Route.ToPhoneCodeRegister)
    }

    fun navigateToEmailCodeRegister() {
        navigateRoute(Route.ToEmailCodeRegister)
    }

    private fun registrationRateLimitMessage(retryAfterSeconds: Long?): String {
        val seconds = retryAfterSeconds?.coerceAtLeast(1) ?: return resourceProvider.getString(
            R.string.module_user_register_rate_limited
        )
        return if (seconds >= 60) {
            resourceProvider.getString(
                R.string.module_user_register_rate_limited_minutes,
                (seconds + 59) / 60
            )
        } else {
            resourceProvider.getString(R.string.module_user_register_rate_limited_seconds, seconds)
        }
    }
}
