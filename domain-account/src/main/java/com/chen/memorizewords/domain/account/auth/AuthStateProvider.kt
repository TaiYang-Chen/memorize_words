package com.chen.memorizewords.domain.account.auth
import kotlinx.coroutines.flow.Flow

interface AuthStateProvider {
    fun isAuthenticated(): Boolean
    fun observeAuthenticated(): Flow<Boolean>
}
