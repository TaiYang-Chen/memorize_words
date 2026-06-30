package com.chen.memorizewords.feature.user.ui.register.phone

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.chen.memorizewords.core.common.resource.ResourceProvider
import com.chen.memorizewords.core.ui.vm.BaseViewModel
import com.chen.memorizewords.domain.account.usecase.user.LoginDataSyncError
import com.chen.memorizewords.domain.account.usecase.user.LoginError
import com.chen.memorizewords.domain.account.usecase.user.PhoneCodeRegisterUseCase
import com.chen.memorizewords.feature.user.R
import com.chen.memorizewords.feature.user.ui.resolveAuthFailureMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@HiltViewModel
class PhoneCodeRegisterViewModel @Inject constructor(
    private val phoneCodeRegisterUseCase: PhoneCodeRegisterUseCase,
    private val resourceProvider: ResourceProvider
) : BaseViewModel() {

    val phone = MutableLiveData("")
    val smsCode = MutableLiveData("")
    val sendCodeText = MutableLiveData(resourceProvider.getString(R.string.module_user_send_code))
    val isSendCodeEnabled = MutableLiveData(true)

    private var verifiedPhone: String? = null
    private var countdownJob: Job? = null

    fun getValidatedPhone(): String? {
        val targetPhone = phone.value.orEmpty().trim()
        return targetPhone.takeIf(::validatePhone)
    }

    fun getValidatedSmsCode(): String? {
        val code = smsCode.value.orEmpty().trim()
        if (code.isBlank()) {
            showToast(resourceProvider.getString(R.string.module_user_bind_phone_code_invalid))
            return null
        }
        return code
    }

    fun showSendingCode() {
        isSendCodeEnabled.value = false
        sendCodeText.value = resourceProvider.getString(R.string.module_user_send_code_sending)
    }

    fun restoreSendCodeButton() {
        sendCodeText.value = resourceProvider.getString(R.string.module_user_send_code)
        isSendCodeEnabled.value = true
    }

    fun markFusionSmsSent(phone: String) {
        verifiedPhone = phone
        showToast(resourceProvider.getString(R.string.module_user_login_sms_sent))
        startCountDown(DEFAULT_RESEND_INTERVAL_SECONDS)
    }

    fun registerWithVerifiedPhone(phone: String, verifyToken: String) {
        if (verifiedPhone != phone) {
            showToast(resourceProvider.getString(R.string.module_user_bind_phone_code_invalid))
            return
        }

        launchWithLoading(resourceProvider.getString(R.string.module_user_register_loading)) {
            phoneCodeRegisterUseCase(phone, verifyToken).onSuccess {
                showToast(resourceProvider.getString(R.string.module_user_register_success))
                finish()
            }.onFailure { failure ->
                when (failure) {
                    is LoginDataSyncError ->
                        showToast(resourceProvider.getString(R.string.module_user_login_sync_failed))

                    is LoginError.EmptyPhone ->
                        showToast(resourceProvider.getString(R.string.module_user_login_phone_required))

                    is LoginError.InvalidPhone ->
                        showToast(resourceProvider.getString(R.string.module_user_bind_phone_phone_invalid))

                    is LoginError.EmptyVerifyToken ->
                        showToast(resourceProvider.getString(R.string.module_user_bind_phone_auth_failed))

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

    private fun validatePhone(value: String): Boolean {
        if (value.isBlank()) {
            showToast(resourceProvider.getString(R.string.module_user_login_phone_required))
            return false
        }
        if (!PHONE_PATTERN.matches(value)) {
            showToast(resourceProvider.getString(R.string.module_user_bind_phone_phone_invalid))
            return false
        }
        return true
    }

    private fun startCountDown(seconds: Int) {
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            isSendCodeEnabled.value = false
            var remain = if (seconds <= 0) DEFAULT_RESEND_INTERVAL_SECONDS else seconds
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
        const val DEFAULT_RESEND_INTERVAL_SECONDS = 60
        val PHONE_PATTERN = "^1[3-9]\\d{9}$".toRegex()
    }
}
