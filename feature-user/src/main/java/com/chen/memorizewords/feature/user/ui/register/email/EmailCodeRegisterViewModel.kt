package com.chen.memorizewords.feature.user.ui.register.email

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.chen.memorizewords.core.common.resource.ResourceProvider
import com.chen.memorizewords.core.ui.vm.BaseViewModel
import com.chen.memorizewords.domain.account.usecase.user.EmailCodeRegisterUseCase
import com.chen.memorizewords.domain.account.usecase.user.LoginDataSyncError
import com.chen.memorizewords.domain.account.usecase.user.LoginError
import com.chen.memorizewords.domain.account.usecase.user.SendEmailCodeUseCase
import com.chen.memorizewords.feature.user.R
import com.chen.memorizewords.feature.user.ui.resolveAuthFailureMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@HiltViewModel
class EmailCodeRegisterViewModel @Inject constructor(
    private val sendEmailCodeUseCase: SendEmailCodeUseCase,
    private val emailCodeRegisterUseCase: EmailCodeRegisterUseCase,
    private val resourceProvider: ResourceProvider
) : BaseViewModel() {

    val email = MutableLiveData("")
    val emailCode = MutableLiveData("")
    val sendCodeText = MutableLiveData(resourceProvider.getString(R.string.module_user_send_code))
    val isSendCodeEnabled = MutableLiveData(true)

    private var countdownJob: Job? = null

    fun sendCode() {
        if (isSendCodeEnabled.value == false) return
        val targetEmail = email.value.orEmpty().trim()
        if (!validateEmail(targetEmail)) return

        showSendCodeSending()
        viewModelScope.launch {
            sendEmailCodeUseCase(targetEmail, scene = REGISTER_EMAIL_SCENE).onSuccess { meta ->
                showToast(resourceProvider.getString(R.string.module_user_login_sms_sent))
                startCountDown(meta.resendIntervalSeconds)
            }.onFailure { failure ->
                restoreSendCodeButton()
                when (failure) {
                    is LoginError.EmptyEmail ->
                        showToast(resourceProvider.getString(R.string.module_user_login_email_required))

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

    fun register() {
        val targetEmail = email.value.orEmpty().trim()
        val code = emailCode.value.orEmpty().trim()
        if (!validateEmail(targetEmail)) return
        if (code.isBlank()) {
            showToast(resourceProvider.getString(R.string.module_user_bind_email_code_invalid))
            return
        }

        launchWithLoading(resourceProvider.getString(R.string.module_user_register_loading)) {
            emailCodeRegisterUseCase(targetEmail, code).onSuccess {
                showToast(resourceProvider.getString(R.string.module_user_register_success))
                finish()
            }.onFailure { failure ->
                when (failure) {
                    is LoginDataSyncError ->
                        showToast(resourceProvider.getString(R.string.module_user_login_sync_failed))

                    is LoginError.EmptyEmail ->
                        showToast(resourceProvider.getString(R.string.module_user_login_email_required))

                    is LoginError.EmptySmsCode ->
                        showToast(resourceProvider.getString(R.string.module_user_bind_email_code_invalid))

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

    private fun validateEmail(value: String): Boolean {
        if (value.isBlank()) {
            showToast(resourceProvider.getString(R.string.module_user_login_email_required))
            return false
        }
        if (!EMAIL_PATTERN.matches(value)) {
            showToast(resourceProvider.getString(R.string.module_user_bind_email_invalid))
            return false
        }
        return true
    }

    private fun showSendCodeSending() {
        isSendCodeEnabled.value = false
        sendCodeText.value = resourceProvider.getString(R.string.module_user_send_code_sending)
    }

    private fun restoreSendCodeButton() {
        sendCodeText.value = resourceProvider.getString(R.string.module_user_send_code)
        isSendCodeEnabled.value = true
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
        const val REGISTER_EMAIL_SCENE = "register"
        const val DEFAULT_RESEND_INTERVAL_SECONDS = 60
        val EMAIL_PATTERN = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$".toRegex()
    }
}
