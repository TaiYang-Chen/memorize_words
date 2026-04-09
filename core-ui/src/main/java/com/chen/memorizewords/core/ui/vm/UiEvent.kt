package com.chen.memorizewords.core.ui.vm

sealed interface UiEvent {

    sealed interface Navigation : UiEvent {
        data object Back : Navigation
        data object Finish : Navigation
        data class Route(val target: Any) : Navigation
    }

    sealed interface Dialog : UiEvent {
        data class CustomConfirmDialog(
            val custom: String,
            val payload: String? = null
        ) : Dialog

        data class ConfirmEdit(
            val action: String? = null,
            val title: String,
            val content: String = "",
            val hint: String = ""
        ) : Dialog

        data class Confirm(
            val action: String? = null,
            val title: String,
            val message: String,
            val confirmText: String = "确定",
            val cancelText: String = "取消"
        ) : Dialog

        data class ConfirmBottom(
            val action: String? = null,
            val title: String,
            val message: String
        ) : Dialog
    }

    data class Toast(val message: String) : UiEvent
}
