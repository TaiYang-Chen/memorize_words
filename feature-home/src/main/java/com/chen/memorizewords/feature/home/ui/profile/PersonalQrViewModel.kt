package com.chen.memorizewords.feature.home.ui.profile

import androidx.lifecycle.viewModelScope
import com.chen.memorizewords.core.ui.vm.BaseViewModel
import com.chen.memorizewords.domain.account.usecase.user.GetUserFlowUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class PersonalQrViewModel @Inject constructor(
    getUserFlowUseCase: GetUserFlowUseCase
) : BaseViewModel() {

    val uiState = getUserFlowUseCase()
        .map { user ->
            val userId = user?.userId?.takeIf { it > 0 } ?: 0L
            val nickname = user?.nickname.orEmpty().trim()
            PersonalQrUiState(
                userId = userId,
                nickname = nickname,
                avatarUrl = user?.avatarUrl,
                payload = PersonalQrPayload.create(userId, nickname)
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = PersonalQrUiState()
        )
}
