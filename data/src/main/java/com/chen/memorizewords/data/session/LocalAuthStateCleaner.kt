package com.chen.memorizewords.data.session

interface LocalAuthStateCleaner {
    suspend fun clearLocalAuthState(notifyKickout: Boolean = true)
}
