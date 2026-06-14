package com.chen.memorizewords.feature.user.ui.profile.email

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.chen.memorizewords.core.common.resource.ResourceProvider
import com.chen.memorizewords.core.ui.vm.BaseViewModel
import com.chen.memorizewords.domain.account.usecase.user.BindEmailUseCase
import com.chen.memorizewords.domain.account.usecase.user.GetUserFlowUseCase
import com.chen.memorizewords.domain.account.usecase.user.LoginError
import com.chen.memorizewords.domain.account.usecase.user.SendEmailCodeUseCase
import com.chen.memorizewords.feature.user.R
import com.chen.memorizewords.feature.user.ui.resolveAuthFailureMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import jakarta.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@HiltViewModel
class BindEmailViewModel @Inject constructor(
    getUserFlowUseCase: GetUserFlowUseCase,
    private val sendEmailCodeUseCase: SendEmailCodeUseCase,
    private val bindEmailUseCase: BindEmailUseCase,
    private val resourceProvider: ResourceProvider
) : BaseViewModel() {

    val email = MutableLiveData("")
    val emailCode = MutableLiveData("")
    val titleText = MutableLiveData(resourceProvider.getString(R.string.module_user_bind_email_title))
    val confirmText = MutableLiveData(resourceProvider.getString(R.string.module_user_bind_email_confirm))
    val sendCodeText = MutableLiveData(resourceProvider.getString(R.string.module_user_send_code))
    val isSendCodeEnabled = MutableLiveData(true)

    private var countdownJob: Job? = null

    init {
        viewModelScope.launch {
            getUserFlowUseCase().collect { user ->
                val hasBoundEmail = !user?.email.isNullOrBlank()
                titleText.value = resourceProvider.getString(
                    if (hasBoundEmail) {
                        R.string.module_user_change_email_title
                    } else {
                        R.string.module_user_bind_email_title
                    }
                )
                confirmText.value = resourceProvider.getString(
                    if (hasBoundEmail) {
                        R.string.module_user_change_email_confirm
                    } else {
                        R.string.module_user_bind_email_confirm
                    }
                )
            }
        }
    }

    fun sendCode() {
        if (isSendCodeEnabled.value == false) return
        val targetEmail = email.value.orEmpty().trim()
        if (!validateEmail(targetEmail)) return

        showSendCodeSending()
        viewModelScope.launch {
            sendEmailCodeUseCase(targetEmail, scene = BIND_EMAIL_SCENE).onSuccess { meta ->
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

    fun confirmBind() {
        val targetEmail = email.value.orEmpty().trim()
        val code = emailCode.value.orEmpty().trim()
        if (!validateEmail(targetEmail)) return
        if (code.isBlank()) {
            showToast(resourceProvider.getString(R.string.module_user_bind_email_code_invalid))
            return
        }

        launchWithLoading(resourceProvider.getString(R.string.module_user_bind_email_loading)) {
            bindEmailUseCase(targetEmail, code).onSuccess {
                showToast(resourceProvider.getString(R.string.module_user_bind_email_success))
                back()
            }.onFailure { failure ->
                when (failure) {
                    is LoginError.EmptyEmail ->
                        showToast(resourceProvider.getString(R.string.module_user_login_email_required))
                    is LoginError.EmptySmsCode ->
                        showToast(resourceProvider.getString(R.string.module_user_bind_email_code_invalid))
                    else -> showToast(
                        resolveAuthFailureMessage(
                            failure,
                            resourceProvider.getString(R.string.module_user_bind_email_failed)
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
        sendCodeText.value = SEND_CODE_LOADING_TEXT
    }

    private fun restoreSendCodeButton() {
        sendCodeText.value = resourceProvider.getString(R.string.module_user_send_code)
        isSendCodeEnabled.value = true
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
        const val BIND_EMAIL_SCENE = "bind_email"
        const val SEND_CODE_LOADING_TEXT = "发送中..."
        val EMAIL_PATTERN = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$".toRegex()
    }
}
