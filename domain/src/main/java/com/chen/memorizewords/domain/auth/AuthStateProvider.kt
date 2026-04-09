package com.chen.memorizewords.domain.auth

import kotlinx.coroutines.flow.Flow

interface AuthStateProvider {
    fun isAuthenticated(): Boolean
    fun observeAuthenticated(): Flow<Boolean>
}
