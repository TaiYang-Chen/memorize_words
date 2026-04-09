package com.chen.memorizewords.domain.auth

interface TokenProvider {
    suspend fun resolveAccessTokenState(): AccessTokenState

    suspend fun getValidAccessToken(): String? = when (val state = resolveAccessTokenState()) {
        is AccessTokenState.Available -> state.token
        AccessTokenState.InvalidSession,
        AccessTokenState.NoSession,
        is AccessTokenState.TemporarilyUnavailable -> null
    }

    fun getAccessTokenIfValid(): String?
}
