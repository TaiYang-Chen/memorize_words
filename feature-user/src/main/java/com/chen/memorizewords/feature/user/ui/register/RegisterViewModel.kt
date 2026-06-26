package com.chen.memorizewords.feature.user.ui.register

import com.chen.memorizewords.core.common.resource.ResourceProvider
import com.chen.memorizewords.core.ui.vm.BaseViewModel
import com.chen.memorizewords.domain.account.usecase.user.LoginDataSyncError
import com.chen.memorizewords.domain.account.usecase.user.LoginError
import com.chen.memorizewords.domain.account.usecase.user.PhoneRegisterUseCase
import com.chen.memorizewords.domain.account.usecase.user.SendRegisterSmsCodeUseCase
import com.chen.memorizewords.feature.user.R
import com.chen.memorizewords.feature.user.ui.resolveAuthFailureMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import jakarta.inject.Inject
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class RegisterViewModel @Inject constructor(
    private val phoneRegisterUseCase: PhoneRegisterUseCase,
    private val sendRegisterSmsCodeUseCase: SendRegisterSmsCodeUseCase,
    private val resourceProvider: ResourceProvider
) : BaseViewModel() {

    val phone = MutableStateFlow("")
    val smsCode = MutableStateFlow("")
    val password = MutableStateFlow("")
    val sendCodeText = MutableStateFlow(resourceProvider.getString(R.string.module_user_send_code))
    val isSendCodeEnabled = MutableStateFlow(true)

    private var countdownJob: Job? = null

    fun sendCode() {
        if (!isSendCodeEnabled.value) return
        showSendCodeSending()
        viewModelScope.launch {
            sendRegisterSmsCodeUseCase(phone.value).onSuccess { meta ->
                showToast(resourceProvider.getString(R.string.module_user_login_sms_sent))
                startCountDown(meta.resendIntervalSeconds)
            }.onFailure { failure ->
                restoreSendCodeButton()
                when (failure) {
                    is LoginError.EmptyPhone ->
                        showToast(resourceProvider.getString(R.string.module_user_login_phone_required))
                    else -> showToast(
                        resolveAuthFailureMessage(
                            failure,
                            resourceProvider.getString(R.string.module_user_send_code_failed)
                        )
                    )
                }
            }
        }
    }

    private fun showSendCodeSending() {
        isSendCodeEnabled.value = false
        sendCodeText.value = SEND_CODE_LOADING_TEXT
    }

    private fun restoreSendCodeButton() {
        sendCodeText.value = resourceProvider.getString(R.string.module_user_send_code)
        isSendCodeEnabled.value = true
    }

    fun register() {
        launchWithLoading("\u6CE8\u518C\u4E2D...") {
            phoneRegisterUseCase(phone.value, smsCode.value, password.value).onSuccess {
                showToast("\u6CE8\u518C\u6210\u529F")
                finish()
            }.onFailure { failure ->
                when (failure) {
                    is LoginDataSyncError ->
                        showToast(resourceProvider.getString(R.string.module_user_login_sync_failed))

                    is LoginError.EmptyPhone ->
                        showToast(resourceProvider.getString(R.string.module_user_login_phone_required))

                    is LoginError.EmptySmsCode ->
                        showToast(resourceProvider.getString(R.string.module_user_login_sms_required))

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

    private fun startCountDown(seconds: Int) {
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            isSendCodeEnabled.value = false
            var remain = if (seconds <= 0) 60 else seconds
            while (remain > 0) {
                sendCodeText.value = resourceProvider.getString(
                    R.string.module_user_countdown_seconds,
                    remain
                )
                delay(1_000)
                remain--
            }
            sendCodeText.value = resourceProvider.getString(R.string.module_user_resend_code)
            isSendCodeEnabled.value = true
        }
    }

    override fun onCleared() {
        countdownJob?.cancel()
        super.onCleared()
    }

    private companion object {
        const val SEND_CODE_LOADING_TEXT = "\u53D1\u9001\u4E2D..."
    }
}
