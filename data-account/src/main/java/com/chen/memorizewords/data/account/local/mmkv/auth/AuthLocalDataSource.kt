package com.chen.memorizewords.data.account.local.mmkv.auth

import com.chen.memorizewords.domain.account.model.user.User
import com.chen.memorizewords.domain.account.auth.LocalAccountStore
import kotlinx.coroutines.flow.Flow

interface AuthLocalDataSource : LocalAccountStore {
    fun getUser(): User?

    override fun getUserFlow(): Flow<User?>

    override fun getUserId(): Long?

    fun saveUser(user: User)
    fun clearUser()
    fun clear()

    fun onboardingCompleted()
}
