package com.chen.memorizewords.feature.user.ui.profile.account

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.chen.memorizewords.core.common.resource.ResourceProvider
import com.chen.memorizewords.core.ui.vm.BaseViewModel
import com.chen.memorizewords.core.ui.vm.UiEffect
import com.chen.memorizewords.domain.usecase.user.DeleteAccountUseCase
import com.chen.memorizewords.feature.user.R
import dagger.hilt.android.lifecycle.HiltViewModel
import jakarta.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@HiltViewModel
class DeleteAccountConfirmViewModel @Inject constructor(
    private val deleteAccountUseCase: DeleteAccountUseCase,
    private val resourceProvider: ResourceProvider
) : BaseViewModel() {

    sealed interface Effect : UiEffect {
        data object NavigateToAuthClearTask : Effect
    }

    val confirmEnabled = MutableLiveData(false)
    val confirmButtonText = MutableLiveData(
        resourceProvider.getString(R.string.module_user_delete_account_confirm_countdown, 10)
    )
    val deleting = MutableLiveData(false)

    private var countdownJob: Job? = null

    init {
        startCountDown()
    }

    fun confirmDelete() {
        if (confirmEnabled.value != true || deleting.value == true) return

        deleting.value = true
        confirmEnabled.value = false
        confirmButtonText.value = resourceProvider.getString(R.string.module_user_delete_account_deleting)

        viewModelScope.launch {
            deleteAccountUseCase().onSuccess {
                showToast(resourceProvider.getString(R.string.module_user_delete_account_submitted))
                emitEffect(Effect.NavigateToAuthClearTask)
            }.onFailure { failure ->
                showToast(
                    failure.message ?: resourceProvider.getString(R.string.module_user_delete_account_failed)
                )
                confirmEnabled.value = true
                confirmButtonText.value = resourceProvider.getString(R.string.module_user_delete_account_confirm)
            }
            deleting.value = false
            if (confirmEnabled.value == true) {
                confirmButtonText.value = resourceProvider.getString(R.string.module_user_delete_account_confirm)
            }
        }
    }

    private fun startCountDown() {
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            for (remaining in 10 downTo 1) {
                confirmButtonText.value = resourceProvider.getString(
                    R.string.module_user_delete_account_confirm_countdown,
                    remaining
                )
                delay(1_000)
            }
            confirmEnabled.value = true
            confirmButtonText.value = resourceProvider.getString(R.string.module_user_delete_account_confirm)
        }
    }

    override fun onCleared() {
        countdownJob?.cancel()
        super.onCleared()
    }
}
