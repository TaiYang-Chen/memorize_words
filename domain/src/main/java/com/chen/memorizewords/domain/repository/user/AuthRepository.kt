package com.chen.memorizewords.domain.repository.user

import com.chen.memorizewords.domain.model.user.User
import com.chen.memorizewords.domain.model.user.SmsCodeMeta
import kotlinx.coroutines.flow.Flow

interface AuthRepository {

    suspend fun login(phoneNumber: String, password: String): Result<User>

    suspend fun sendLoginSmsCode(phone: String): Result<SmsCodeMeta>

    suspend fun loginBySms(phone: String, code: String): Result<User>

    suspend fun loginByWechat(oauthCode: String, state: String? = null): Result<User>

    suspend fun loginByQq(oauthCode: String, state: String? = null): Result<User>

    suspend fun register(phoneNumber: String, password: String): Result<User>

    suspend fun changePassword(oldPassword: String, newPassword: String): Result<Unit>

    suspend fun bindSocial(platform: String, oauthCode: String, state: String?): Result<User>

    suspend fun logout(force: Boolean = false): Result<Unit>

    suspend fun deleteAccount(): Result<Unit>

    fun isLoggedIn(): Boolean

    suspend fun getCurrentUser(): User?

    fun getUserFlow(): Flow<User?>
}
