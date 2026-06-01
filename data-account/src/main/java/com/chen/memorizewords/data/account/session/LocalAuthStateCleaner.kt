package com.chen.memorizewords.data.account.session

interface LocalAuthStateCleaner {
    suspend fun clearLocalAuthState(notifyKickout: Boolean = true)
}
