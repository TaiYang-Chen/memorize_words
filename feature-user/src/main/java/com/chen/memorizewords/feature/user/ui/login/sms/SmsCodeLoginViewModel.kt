package com.chen.memorizewords.feature.user.ui.login.sms

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.chen.memorizewords.core.common.resource.ResourceProvider
import com.chen.memorizewords.core.ui.vm.BaseViewModel
import com.chen.memorizewords.domain.usecase.user.LoginError
import com.chen.memorizewords.domain.usecase.user.SendLoginSmsCodeUseCase
import com.chen.memorizewords.domain.usecase.user.SmsLoginUseCase
import com.chen.memorizewords.feature.user.R
import com.chen.memorizewords.feature.user.ui.resolveAuthFailureMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import jakarta.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@HiltViewModel
class SmsCodeLoginViewModel @Inject constructor(
    private val sendLoginSmsCodeUseCase: SendLoginSmsCodeUseCase,
    private val smsLoginUseCase: SmsLoginUseCase,
    private val resourceProvider: ResourceProvider
) : BaseViewModel() {

    val phoneNumber = MutableLiveData("")
    val smsCode = MutableLiveData("")
    val sendCodeText = MutableLiveData(resourceProvider.getString(R.string.module_user_send_code))
    val isSendCodeEnabled = MutableLiveData(true)

    private var countdownJob: Job? = null

    fun sendCode() {
        if (isSendCodeEnabled.value == false) return
        viewModelScope.launch {
            sendLoginSmsCodeUseCase(phoneNumber.value.orEmpty()).onSuccess { meta ->
                showToast(resourceProvider.getString(R.string.module_user_login_sms_sent))
                startCountDown(meta.resendIntervalSeconds)
            }.onFailure { failure ->
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

    fun loginBySms() {
        launchWithLoading("\u767B\u5F55\u4E2D...") {
            smsLoginUseCase(
                phone = phoneNumber.value.orEmpty(),
                code = smsCode.value.orEmpty()
            ).onSuccess {
                showToast(resourceProvider.getString(R.string.module_user_login_success))
                finish()
            }.onFailure { failure ->
                when (failure) {
                    is LoginError.EmptyPhone ->
                        showToast(resourceProvider.getString(R.string.module_user_login_phone_required))
                    is LoginError.EmptySmsCode ->
                        showToast(resourceProvider.getString(R.string.module_user_login_sms_required))
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
}
