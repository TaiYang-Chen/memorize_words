package com.chen.memorizewords.feature.user.ui.login.phone

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.chen.memorizewords.core.common.resource.ResourceProvider
import com.chen.memorizewords.core.ui.vm.BaseViewModel
import com.chen.memorizewords.domain.account.usecase.user.LoginDataSyncError
import com.chen.memorizewords.domain.account.usecase.user.LoginError
import com.chen.memorizewords.domain.account.usecase.user.PhoneCodeLoginUseCase
import com.chen.memorizewords.feature.user.R
import com.chen.memorizewords.feature.user.ui.resolveAuthFailureMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@HiltViewModel
class PhoneCodeLoginViewModel @Inject constructor(
    private val phoneCodeLoginUseCase: PhoneCodeLoginUseCase,
    private val resourceProvider: ResourceProvider
) : BaseViewModel() {

    val phone = MutableLiveData("")
    val smsCode = MutableLiveData("")
    val sendCodeText = MutableLiveData(resourceProvider.getString(R.string.module_user_send_code))
    val isSendCodeEnabled = MutableLiveData(true)

    private var verifiedPhone: String? = null
    private var pendingPhone: String? = null
    private var pendingVerifyToken: String? = null
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

    fun loginWithVerifiedPhone(phone: String, verifyToken: String) {
        if (verifiedPhone != phone) {
            showToast(resourceProvider.getString(R.string.module_user_bind_phone_code_invalid))
            return
        }
        login(phone = phone, verifyToken = verifyToken, cancelDeletion = false)
    }

    fun confirmCancelDeletionAndLogin() {
        val phone = pendingPhone
        val verifyToken = pendingVerifyToken
        if (phone.isNullOrBlank() || verifyToken.isNullOrBlank()) {
            showToast(resourceProvider.getString(R.string.module_user_login_failed))
            return
        }
        login(phone = phone, verifyToken = verifyToken, cancelDeletion = true)
    }

    private fun login(phone: String, verifyToken: String, cancelDeletion: Boolean) {
        pendingPhone = phone
        pendingVerifyToken = verifyToken
        launchWithLoading(resourceProvider.getString(R.string.module_user_login_loading)) {
            phoneCodeLoginUseCase(phone, verifyToken, cancelDeletion).onSuccess {
                showToast(resourceProvider.getString(R.string.module_user_login_success))
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

                    is LoginError.AccountDeletionPending ->
                        showCancelDeletionConfirmDialog()

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

    private fun showCancelDeletionConfirmDialog() {
        showConfirmDialog(
            action = ACTION_CANCEL_DELETION_LOGIN,
            title = resourceProvider.getString(R.string.module_user_account_deletion_pending_title),
            message = resourceProvider.getString(R.string.module_user_account_deletion_pending_message),
            confirmText = resourceProvider.getString(R.string.module_user_account_deletion_pending_confirm),
            cancelText = resourceProvider.getString(R.string.module_user_account_deletion_pending_cancel)
        )
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

    companion object {
        const val ACTION_CANCEL_DELETION_LOGIN = "phone_code_cancel_deletion_login"
        private const val DEFAULT_RESEND_INTERVAL_SECONDS = 60
        private val PHONE_PATTERN = "^1[3-9]\\d{9}$".toRegex()
    }
}
