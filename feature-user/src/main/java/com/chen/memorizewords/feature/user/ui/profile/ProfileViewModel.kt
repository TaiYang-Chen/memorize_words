package com.chen.memorizewords.feature.user.ui.profile

import androidx.lifecycle.viewModelScope
import com.chen.memorizewords.core.common.resource.ResourceProvider
import com.chen.memorizewords.core.ui.vm.BaseViewModel
import com.chen.memorizewords.core.ui.vm.UiEvent
import com.chen.memorizewords.domain.model.user.User
import com.chen.memorizewords.domain.usecase.user.BindSocialUseCase
import com.chen.memorizewords.domain.usecase.user.ChangeAvatarUseCase
import com.chen.memorizewords.domain.usecase.user.ChangeGenderUseCase
import com.chen.memorizewords.domain.usecase.user.ChangeNicknameUseCase
import com.chen.memorizewords.domain.usecase.user.GetUserFlowUseCase
import com.chen.memorizewords.feature.user.R
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    getUserFlowUseCase: GetUserFlowUseCase,
    private val changeNicknameUseCase: ChangeNicknameUseCase,
    private val changeGenderUseCase: ChangeGenderUseCase,
    private val changeAvatarUseCase: ChangeAvatarUseCase,
    private val bindSocialUseCase: BindSocialUseCase,
    private val resourceProvider: ResourceProvider
) : BaseViewModel() {

    companion object {
        const val ACTION_CHANGE_NICKNAME = "change_nickname"
        const val DIALOG_CHANGE_GENDER = "changeGender"
        const val DIALOG_AVATAR_ACTIONS = "avatarActions"
        const val DIALOG_BIND_WECHAT = "bindWechat"
        const val DIALOG_BIND_QQ = "bindQq"
    }

    sealed interface Route {
        data class ToAvatarPreview(val avatarSource: String) : Route
        data object ToChangePassword : Route
        data object ToDeleteAccountConfirm : Route
        data object ToBindPhone : Route
    }

    val user: StateFlow<User?> =
        getUserFlowUseCase()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = null
            )

    fun changeNickname() {
        showConfirmEditDialog(
            action = ACTION_CHANGE_NICKNAME,
            resourceProvider.getString(R.string.module_user_profile_nickname_title),
        )
    }

    fun changeGender() {
        emitEvent(UiEvent.Dialog.CustomConfirmDialog(DIALOG_CHANGE_GENDER))
    }

    fun changePhoneNumber() {
        toBindPhone()
    }

    fun changeWechat() {
        startBindWechat()
    }

    fun changeWeChart() {
        changeWechat()
    }

    fun changeQQ() {
        startBindQq()
    }

    fun onAvatarClick() {
        emitEvent(UiEvent.Dialog.CustomConfirmDialog(DIALOG_AVATAR_ACTIONS))
    }

    fun openAvatarPreview() {
        navigateRoute(Route.ToAvatarPreview(user.value?.avatarUrl.orEmpty()))
    }

    fun changeAvatar(imageBytes: ByteArray) {
        viewModelScope.launch {
            changeAvatarUseCase(imageBytes).onSuccess {
                showToast(resourceProvider.getString(R.string.module_user_profile_avatar_update_success))
            }.onFailure { failure ->
                val message = failure.message.orEmpty()
                if (message.contains("404") || message.contains("501")) {
                    showToast(resourceProvider.getString(R.string.module_user_profile_avatar_coming_soon))
                } else {
                    showToast(message.ifBlank {
                        resourceProvider.getString(R.string.module_user_profile_avatar_update_failed)
                    })
                }
            }
        }
    }

    fun toChangePassword() {
        navigateRoute(Route.ToChangePassword)
    }

    fun toDeleteAccountConfirm() {
        navigateRoute(Route.ToDeleteAccountConfirm)
    }

    fun toBindPhone() {
        navigateRoute(Route.ToBindPhone)
    }

    fun startBindWechat() {
        emitEvent(UiEvent.Dialog.CustomConfirmDialog(DIALOG_BIND_WECHAT))
    }

    fun startBindQq() {
        emitEvent(UiEvent.Dialog.CustomConfirmDialog(DIALOG_BIND_QQ))
    }

    fun confirmNicknameChange(nickname: String) {
        viewModelScope.launch {
            changeNicknameUseCase(nickname).onSuccess {
                showToast(resourceProvider.getString(R.string.module_user_profile_update_success))
            }.onFailure { failure ->
                showToast(message = failure.message.orEmpty())
            }
        }
    }

    fun confirmGenderChange(gender: String) {
        viewModelScope.launch {
            changeGenderUseCase(gender).onSuccess {
                showToast(resourceProvider.getString(R.string.module_user_profile_update_success))
            }.onFailure { failure ->
                showToast(message = failure.message.orEmpty())
            }
        }
    }

    fun bindWechat(oauthCode: String, state: String?) {
        bindSocial(platform = "wechat", oauthCode = oauthCode, state = state)
    }

    fun bindQq(oauthCode: String, state: String?) {
        bindSocial(platform = "qq", oauthCode = oauthCode, state = state)
    }

    private fun bindSocial(platform: String, oauthCode: String, state: String?) {
        viewModelScope.launch {
            bindSocialUseCase(
                platform = platform,
                oauthCode = oauthCode,
                state = state
            ).onSuccess {
                showToast(resourceProvider.getString(R.string.module_user_profile_bind_success))
            }.onFailure { failure ->
                val message = failure.message.orEmpty()
                if (message.contains("404") || message.contains("501")) {
                    showToast(resourceProvider.getString(R.string.module_user_profile_bind_coming_soon))
                } else {
                    showToast(message.ifBlank {
                        resourceProvider.getString(R.string.module_user_profile_bind_failed)
                    })
                }
            }
        }
    }
}
