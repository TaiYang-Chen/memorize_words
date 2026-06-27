package com.chen.memorizewords.domain.account.repository.user
import com.chen.memorizewords.domain.account.model.AuthLoginResult
import com.chen.memorizewords.domain.account.model.FusionAuthToken
import com.chen.memorizewords.domain.account.model.user.User
import com.chen.memorizewords.domain.account.model.user.SmsCodeMeta

interface AuthRepository {

    suspend fun loginByPassword(
        phoneNumber: String,
        password: String,
        cancelDeletion: Boolean = false
    ): Result<AuthLoginResult>

    suspend fun sendEmailCode(email: String, scene: String = "login"): Result<SmsCodeMeta>

    suspend fun loginByEmailCode(
        email: String,
        code: String,
        cancelDeletion: Boolean = false
    ): Result<AuthLoginResult>

    suspend fun loginByWechat(
        oauthCode: String,
        state: String? = null,
        cancelDeletion: Boolean = false
    ): Result<AuthLoginResult>

    suspend fun loginByQq(
        oauthCode: String,
        state: String? = null,
        cancelDeletion: Boolean = false
    ): Result<AuthLoginResult>

    suspend fun register(email: String, emailCode: String, password: String): Result<AuthLoginResult>

    suspend fun getFusionAuthToken(): Result<FusionAuthToken>

    suspend fun loginByFusionVerifyToken(
        verifyToken: String,
        cancelDeletion: Boolean = false
    ): Result<AuthLoginResult>

    suspend fun registerByFusionVerifyToken(verifyToken: String, password: String): Result<AuthLoginResult>

    suspend fun changePassword(oldPassword: String, newPassword: String): Result<Unit>

    suspend fun onboardingCompleted(): Result<Unit>

    suspend fun bindSocial(platform: String, oauthCode: String, state: String?): Result<User>

    suspend fun logoutRemote(): Result<Unit>

    suspend fun deleteAccountRemote(): Result<Unit>
}
