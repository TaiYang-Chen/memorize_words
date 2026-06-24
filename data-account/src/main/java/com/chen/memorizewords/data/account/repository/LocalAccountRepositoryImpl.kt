package com.chen.memorizewords.data.account.repository

import com.chen.memorizewords.data.account.local.avatar.AvatarLocalDataSource
import com.chen.memorizewords.data.account.local.mmkv.auth.AuthLocalDataSource
import com.chen.memorizewords.domain.account.auth.AuthStateProvider
import com.chen.memorizewords.domain.account.model.user.User
import com.chen.memorizewords.domain.account.repository.LocalAccountRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class LocalAccountRepositoryImpl @Inject constructor(
    private val authLocal: AuthLocalDataSource,
    private val authStateProvider: AuthStateProvider,
    private val avatarLocal: AvatarLocalDataSource
) : LocalAccountRepository {
    override fun isLoggedIn(): Boolean {
        return authStateProvider.isAuthenticated()
    }

    override suspend fun getCurrentUser(): User? {
        return authLocal.getUser()
    }

    override suspend fun getCurrentUserId(): Long? {
        return authLocal.getUserId()
    }

    override fun getUserFlow(): Flow<User?> {
        return authLocal.getUserFlow()
    }

    override suspend fun saveUser(user: User) {
        authLocal.saveUser(user)
    }

    override suspend fun clearUser() {
        val avatarPath = authLocal.getUser()?.localAvatarPath
        authLocal.clearUser()
        avatarLocal.deleteAvatar(avatarPath)
    }
}
