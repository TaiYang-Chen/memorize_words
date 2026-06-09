package com.chen.memorizewords.domain.account.repository.user
import com.chen.memorizewords.domain.account.model.AuthLoginResult
import com.chen.memorizewords.domain.account.model.user.User
import com.chen.memorizewords.domain.account.model.user.SmsCodeMeta

interface AuthRepository {

    suspend fun loginByPassword(phoneNumber: String, password: String): Result<AuthLoginResult>

    suspend fun sendLoginSmsCode(phone: String): Result<SmsCodeMeta>

    suspend fun sendEmailCode(email: String, scene: String = "login"): Result<SmsCodeMeta>

    suspend fun loginBySms(phone: String, code: String): Result<AuthLoginResult>

    suspend fun loginByEmailCode(email: String, code: String): Result<AuthLoginResult>

    suspend fun loginByWechat(oauthCode: String, state: String? = null): Result<AuthLoginResult>

    suspend fun loginByQq(oauthCode: String, state: String? = null): Result<AuthLoginResult>

    suspend fun register(email: String, emailCode: String, password: String): Result<AuthLoginResult>

    suspend fun changePassword(oldPassword: String, newPassword: String): Result<Unit>

    suspend fun bindSocial(platform: String, oauthCode: String, state: String?): Result<User>

    suspend fun logoutRemote(): Result<Unit>

    suspend fun deleteAccountRemote(): Result<Unit>
}
