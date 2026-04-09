package com.chen.memorizewords.domain.auth

sealed interface AccessTokenState {
    data class Available(val token: String) : AccessTokenState
    data object NoSession : AccessTokenState
    data object InvalidSession : AccessTokenState
    data class TemporarilyUnavailable(val cause: Throwable) : AccessTokenState
}
