package com.chen.memorizewords.domain.account.repository

import com.chen.memorizewords.domain.account.model.user.User
import kotlinx.coroutines.flow.Flow

interface LocalAccountRepository {
    fun isLoggedIn(): Boolean

    suspend fun getCurrentUser(): User?

    suspend fun getCurrentUserId(): Long?

    fun getUserFlow(): Flow<User?>

    suspend fun saveUser(user: User)

    suspend fun clearUser()
}
