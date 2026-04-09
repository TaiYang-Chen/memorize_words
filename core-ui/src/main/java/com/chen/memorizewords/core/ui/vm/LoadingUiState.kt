package com.chen.memorizewords.core.ui.vm

data class LoadingUiState(
    val isLoading: Boolean = false,
    val message: String = BaseViewModel.DEFAULT_LOADING_MESSAGE
)
