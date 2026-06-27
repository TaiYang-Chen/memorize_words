package com.chen.memorizewords.feature.user.ui.login

import com.chen.memorizewords.core.common.resource.ResourceProvider
import com.chen.memorizewords.core.ui.vm.BaseViewModel
import com.chen.memorizewords.domain.account.usecase.user.LoginDataSyncError
import com.chen.memorizewords.domain.account.usecase.user.LoginError
import com.chen.memorizewords.domain.account.usecase.user.LoginUseCase
import com.chen.memorizewords.feature.user.R
import com.chen.memorizewords.feature.user.ui.resolveAuthFailureMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import jakarta.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val loginUseCase: LoginUseCase,
    private val resourceProvider: ResourceProvider
) : BaseViewModel() {

    sealed interface Route {
        data object ToRegister : Route
        data object ToWeChatOneTapLogin : Route
        data object ToQQOneTapLogin : Route
        data object ToFusionPhoneLogin : Route
    }

    val email = MutableStateFlow("1563011912@qq.com")
    val password = MutableStateFlow("123456")

    fun login() {
        login(cancelDeletion = false)
    }

    fun confirmCancelDeletionAndLogin() {
        login(cancelDeletion = true)
    }

    private fun login(cancelDeletion: Boolean) {
        launchWithLoading(resourceProvider.getString(R.string.module_user_login_loading)) {
            loginUseCase(email.value, password.value, cancelDeletion).onSuccess {
                showToast(resourceProvider.getString(R.string.module_user_login_success))
                finish()
            }.onFailure { failure ->
                when (failure) {
                    is LoginDataSyncError ->
                        showToast(resourceProvider.getString(R.string.module_user_login_sync_failed))

                    is LoginError.EmptyEmail ->
                        showToast(resourceProvider.getString(R.string.module_user_login_email_required))

                    is LoginError.EmptyPassword ->
                        showToast(resourceProvider.getString(R.string.module_user_login_password_required))

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

    private fun showCancelDeletionConfirmDialog() {
        showConfirmDialog(
            action = ACTION_CANCEL_DELETION_LOGIN,
            title = resourceProvider.getString(R.string.module_user_account_deletion_pending_title),
            message = resourceProvider.getString(R.string.module_user_account_deletion_pending_message),
            confirmText = resourceProvider.getString(R.string.module_user_account_deletion_pending_confirm),
            cancelText = resourceProvider.getString(R.string.module_user_account_deletion_pending_cancel)
        )
    }

    fun navigateToRegister() {
        navigateRoute(Route.ToRegister)
    }

    fun navigateToWeChatOneTapLogin() {
        navigateRoute(Route.ToWeChatOneTapLogin)
    }

    fun navigateToQQOneTapLogin() {
        navigateRoute(Route.ToQQOneTapLogin)
    }

    fun navigateToFusionPhoneLogin() {
        navigateRoute(Route.ToFusionPhoneLogin)
    }

    companion object {
        const val ACTION_CANCEL_DELETION_LOGIN = "cancel_deletion_login"
    }
}
