package com.chen.memorizewords.data.local.mmkv.auth

import com.chen.memorizewords.domain.model.user.User
import kotlinx.coroutines.flow.Flow

interface AuthLocalDataSource {
    fun getUser(): User?

    fun getUserFlow(): Flow<User?>

    fun getUserId(): Long?

    fun saveUser(user: User)
    fun clear()
}