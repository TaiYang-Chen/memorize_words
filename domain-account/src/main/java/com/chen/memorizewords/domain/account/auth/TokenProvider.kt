package com.chen.memorizewords.domain.account.auth
interface TokenProvider {
    suspend fun resolveAccessTokenState(notifyKickoutOnInvalidSession: Boolean = true): AccessTokenState

    fun getAccessTokenIfValid(): String?
}
