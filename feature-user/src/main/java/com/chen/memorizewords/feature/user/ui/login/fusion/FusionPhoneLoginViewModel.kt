package com.chen.memorizewords.feature.user.ui.login.fusion

import com.chen.memorizewords.core.common.resource.ResourceProvider
import com.chen.memorizewords.core.ui.vm.BaseViewModel
import com.chen.memorizewords.domain.account.usecase.user.FusionPhoneLoginUseCase
import com.chen.memorizewords.domain.account.usecase.user.LoginDataSyncError
import com.chen.memorizewords.domain.account.usecase.user.LoginError
import com.chen.memorizewords.feature.user.R
import com.chen.memorizewords.feature.user.ui.resolveAuthFailureMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class FusionPhoneLoginViewModel @Inject constructor(
    private val fusionPhoneLoginUseCase: FusionPhoneLoginUseCase,
    private val resourceProvider: ResourceProvider
) : BaseViewModel() {

    private var pendingVerifyToken: String? = null

    fun loginByFusionVerifyToken(verifyToken: String) {
        pendingVerifyToken = verifyToken
        loginByFusionVerifyToken(verifyToken, cancelDeletion = false)
    }

    fun confirmCancelDeletionAndLogin() {
        val verifyToken = pendingVerifyToken
        if (verifyToken.isNullOrBlank()) {
            showToast(resourceProvider.getString(R.string.module_user_account_deletion_restore_failed))
            return
        }
        loginByFusionVerifyToken(verifyToken, cancelDeletion = true)
    }

    private fun loginByFusionVerifyToken(verifyToken: String, cancelDeletion: Boolean) {
        launchWithLoading(resourceProvider.getString(R.string.module_user_login_loading)) {
            fusionPhoneLoginUseCase(verifyToken, cancelDeletion).onSuccess {
                showToast(resourceProvider.getString(R.string.module_user_login_success))
                finish()
            }.onFailure { failure ->
                when (failure) {
                    is LoginDataSyncError ->
                        showToast(resourceProvider.getString(R.string.module_user_login_sync_failed))

                    is LoginError.EmptyOauthCode ->
                        showToast(resourceProvider.getString(R.string.module_user_fusion_login_failed))

                    is LoginError.AccountDeletionPending ->
                        showCancelDeletionConfirmDialog()

                    else -> showToast(
                        resolveAuthFailureMessage(
                            failure,
                            resourceProvider.getString(R.string.module_user_fusion_login_failed)
                        )
                    )
                }
            }
        }
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

    companion object {
        const val ACTION_CANCEL_DELETION_LOGIN = "fusion_cancel_deletion_login"
    }
}
