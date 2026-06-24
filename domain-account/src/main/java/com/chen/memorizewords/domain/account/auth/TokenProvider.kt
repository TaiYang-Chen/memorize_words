package com.chen.memorizewords.domain.account.auth
interface TokenProvider {
    suspend fun resolveAccessTokenState(notifyKickoutOnInvalidSession: Boolean = true): AccessTokenState

    suspend fun getValidAccessToken(): String? = when (val state = resolveAccessTokenState()) {
        is AccessTokenState.Available -> state.token
        AccessTokenState.InvalidSession,
        AccessTokenState.NoSession,
        is AccessTokenState.TemporarilyUnavailable -> null
    }

    fun getAccessTokenIfValid(): String?
}
