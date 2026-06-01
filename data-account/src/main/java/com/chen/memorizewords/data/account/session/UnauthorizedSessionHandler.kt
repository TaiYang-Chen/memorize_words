package com.chen.memorizewords.data.account.session

class UnauthorizedSessionHandler(
    private val localAuthSessionCleaner: LocalAuthStateCleaner
) : com.chen.memorizewords.domain.account.auth.UnauthorizedSessionHandler,
    com.chen.memorizewords.core.network.remote.UnauthorizedNetworkHandler {

    override suspend fun handleUnauthorized() {
        localAuthSessionCleaner.clearLocalAuthState()
    }
}
