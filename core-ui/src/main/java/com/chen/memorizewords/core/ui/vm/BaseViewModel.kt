package com.chen.memorizewords.core.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chen.memorizewords.core.navigation.AppRoute
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

open class BaseViewModel : ViewModel() {

    companion object {
        const val DEFAULT_LOADING_MESSAGE = "\u52A0\u8F7D\u4E2D..."
    }

    private val _uiEvent = MutableSharedFlow<UiEvent>(extraBufferCapacity = 16)
    val uiEvent = _uiEvent.asSharedFlow()

    private val loadingCounter = AtomicInteger(0)
    private val _loadingState = MutableStateFlow(LoadingUiState())
    val loadingState: StateFlow<LoadingUiState> = _loadingState.asStateFlow()

    protected fun emitEvent(event: UiEvent) {
        _uiEvent.tryEmit(event)
    }

    protected fun emitEffect(effect: UiEffect) {
        emitEvent(effect)
    }

    fun showLoading(message: String = DEFAULT_LOADING_MESSAGE) {
        loadingCounter.incrementAndGet()
        _loadingState.value = LoadingUiState(isLoading = true, message = message)
    }

    fun hideLoading() {
        val remain = loadingCounter.updateAndGet { count ->
            if (count > 0) count - 1 else 0
        }
        if (remain == 0) {
            _loadingState.value = LoadingUiState()
        }
    }

    fun updateLoadingMessage(message: String) {
        if (_loadingState.value.isLoading) {
            _loadingState.value = LoadingUiState(isLoading = true, message = message)
        }
    }

    protected fun launchWithLoading(
        message: String = DEFAULT_LOADING_MESSAGE,
        block: suspend CoroutineScope.() -> Unit
    ): Job {
        return viewModelScope.launch {
            showLoading(message)
            try {
                block()
            } finally {
                hideLoading()
            }
        }
    }

    fun finish() {
        emitEvent(UiEvent.Navigation.Finish)
    }

    fun back() {
        emitEvent(UiEvent.Navigation.Back)
    }

    protected fun navigateRoute(target: Any) {
        emitEvent(UiEvent.Navigation.Route(target))
    }

    protected fun navigate(route: AppRoute) {
        navigateRoute(route)
    }

    fun showToast(message: String) {
        emitEvent(UiEvent.Toast(message))
    }

    fun showConfirmEditDialog(
        action: String? = null,
        title: String,
        content: String = "",
        hint: String = ""
    ) {
        emitEvent(UiEvent.Dialog.ConfirmEdit(action, title, content, hint))
    }

    fun showConfirmDialog(
        action: String? = null,
        title: String,
        message: String,
        confirmText: String = "\u786e\u5b9a",
        cancelText: String = "\u53d6\u6d88"
    ) {
        emitEvent(UiEvent.Dialog.Confirm(action, title, message, confirmText, cancelText))
    }

    fun showSingleConfirmDialog(
        action: String? = null,
        title: String,
        message: String,
        confirmText: String = "\u786e\u5b9a"
    ) {
        emitEvent(UiEvent.Dialog.SingleConfirm(action, title, message, confirmText))
    }

    override fun onCleared() {
        loadingCounter.set(0)
        _loadingState.value = LoadingUiState()
        super.onCleared()
    }
}
