package com.chen.memorizewords.feature.user.ui.login.wechat

import com.chen.memorizewords.core.common.resource.ResourceProvider
import com.chen.memorizewords.core.ui.vm.BaseViewModel
import com.chen.memorizewords.domain.account.usecase.user.LoginDataSyncError
import com.chen.memorizewords.domain.account.usecase.user.LoginError
import com.chen.memorizewords.domain.account.usecase.user.WeChatLoginUseCase
import com.chen.memorizewords.feature.user.R
import com.chen.memorizewords.feature.user.ui.resolveAuthFailureMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import jakarta.inject.Inject

@HiltViewModel
class WeChatOneTapLoginViewModel @Inject constructor(
    private val wechatLoginUseCase: WeChatLoginUseCase,
    private val resourceProvider: ResourceProvider
) : BaseViewModel() {

    private var pendingOauthCode: String? = null
    private var pendingState: String? = null

    fun loginByWechat(oauthCode: String, state: String?) {
        pendingOauthCode = oauthCode
        pendingState = state
        loginByWechat(oauthCode, state, cancelDeletion = false)
    }

    fun confirmCancelDeletionAndLogin() {
        val oauthCode = pendingOauthCode
        if (oauthCode.isNullOrBlank()) {
            showToast(resourceProvider.getString(R.string.module_user_account_deletion_restore_failed))
            return
        }
        loginByWechat(oauthCode, pendingState, cancelDeletion = true)
    }

    private fun loginByWechat(oauthCode: String, state: String?, cancelDeletion: Boolean) {
        launchWithLoading(resourceProvider.getString(R.string.module_user_login_loading)) {
            wechatLoginUseCase(oauthCode, state, cancelDeletion).onSuccess {
                showToast(resourceProvider.getString(R.string.module_user_login_success))
                finish()
            }.onFailure { failure ->
                when (failure) {
                    is LoginDataSyncError ->
                        showToast(resourceProvider.getString(R.string.module_user_login_sync_failed))

                    is LoginError.EmptyOauthCode ->
                        showToast(resourceProvider.getString(R.string.module_user_wechat_auth_failed))

                    is LoginError.AccountDeletionPending ->
                        showCancelDeletionConfirmDialog()

                    else -> showToast(
                        resolveAuthFailureMessage(
                            failure,
                            resourceProvider.getString(R.string.module_user_wechat_login_failed)
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
        const val ACTION_CANCEL_DELETION_LOGIN = "wechat_cancel_deletion_login"
    }
}
