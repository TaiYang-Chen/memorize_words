package com.chen.memorizewords.feature.user.ui.profile.password

import androidx.lifecycle.viewModelScope
import com.chen.memorizewords.core.common.resource.ResourceProvider
import com.chen.memorizewords.core.ui.vm.BaseViewModel
import com.chen.memorizewords.domain.usecase.user.ChangePasswordUseCase
import com.chen.memorizewords.domain.usecase.user.LogoutUseCase
import com.chen.memorizewords.feature.user.R
import dagger.hilt.android.lifecycle.HiltViewModel
import jakarta.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class ChangePasswordViewModel @Inject constructor(
    private val changePasswordUseCase: ChangePasswordUseCase,
    private val logoutUseCase: LogoutUseCase,
    private val resourceProvider: ResourceProvider
) : BaseViewModel() {
    sealed interface Route {
        data object ToLogin : Route
    }

    val oldPassword = MutableStateFlow("")
    val newPassword = MutableStateFlow("")
    val confirmPassword = MutableStateFlow("")

    fun submit() {
        val oldPwd = oldPassword.value
        val newPwd = newPassword.value
        val confirmPwd = confirmPassword.value

        if (oldPwd.isBlank()) {
            showToast(resourceProvider.getString(R.string.module_user_change_pwd_old_required))
            return
        }
        if (newPwd.isBlank()) {
            showToast(resourceProvider.getString(R.string.module_user_change_pwd_new_required))
            return
        }
        if (confirmPwd.isBlank()) {
            showToast(resourceProvider.getString(R.string.module_user_change_pwd_confirm_required))
            return
        }
        if (newPwd.length < 6) {
            showToast(resourceProvider.getString(R.string.module_user_change_pwd_min_length))
            return
        }
        if (oldPwd == newPwd) {
            showToast(resourceProvider.getString(R.string.module_user_change_pwd_diff_old))
            return
        }
        if (newPwd != confirmPwd) {
            showToast(resourceProvider.getString(R.string.module_user_change_pwd_not_match))
            return
        }

        viewModelScope.launch {
            changePasswordUseCase(oldPwd, newPwd).onSuccess {
                logoutUseCase(force = true)
                showToast(resourceProvider.getString(R.string.module_user_change_pwd_success_relogin))
                navigateRoute(Route.ToLogin)
            }.onFailure { failure ->
                val message = failure.message.orEmpty()
                if (message.contains("404") || message.contains("501")) {
                    showToast(resourceProvider.getString(R.string.module_user_change_pwd_coming_soon))
                } else {
                    showToast(message.ifBlank {
                        resourceProvider.getString(R.string.module_user_change_pwd_failed)
                    })
                }
            }
        }
    }
}
