package com.chen.memorizewords.domain.account.auth

import com.chen.memorizewords.domain.account.model.user.User
import kotlinx.coroutines.flow.Flow

interface LocalAccountStore {
    fun getUserId(): Long?
    fun getUserFlow(): Flow<User?>
}

interface UnauthorizedSessionHandler {
    suspend fun handleUnauthorized()
}
