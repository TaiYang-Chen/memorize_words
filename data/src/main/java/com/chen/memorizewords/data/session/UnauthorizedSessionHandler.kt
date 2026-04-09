package com.chen.memorizewords.data.session

class UnauthorizedSessionHandler(
    private val localAuthSessionCleaner: LocalAuthStateCleaner
) {

    suspend fun handleUnauthorized() {
        localAuthSessionCleaner.clearLocalAuthState()
    }
}
