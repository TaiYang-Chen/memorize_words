package com.chen.memorizewords.domain.account.repository.user
import com.chen.memorizewords.domain.account.model.AuthLoginResult
import com.chen.memorizewords.domain.account.model.AuthIdentifierType
import com.chen.memorizewords.domain.account.model.FusionAuthToken
import com.chen.memorizewords.domain.account.model.user.User
import com.chen.memorizewords.domain.account.model.user.SmsCodeMeta

interface AuthRepository {

    suspend fun loginByPassword(
        identifier: String,
        identifierType: AuthIdentifierType,
        password: String,
        cancelDeletion: Boolean = false
    ): Result<AuthLoginResult>

    suspend fun sendEmailCode(email: String, scene: String = "login"): Result<SmsCodeMeta>

    suspend fun loginByEmailCode(
        email: String,
        code: String,
        cancelDeletion: Boolean = false
    ): Result<AuthLoginResult>

    suspend fun loginByPhoneCode(
        phone: String,
        verifyToken: String,
        cancelDeletion: Boolean = false
    ): Result<AuthLoginResult>

    suspend fun registerByAccount(account: String, password: String): Result<AuthLoginResult>

    suspend fun registerByEmailCode(email: String, emailCode: String): Result<AuthLoginResult>

    suspend fun registerByPhoneCode(phone: String, verifyToken: String): Result<AuthLoginResult>

    suspend fun getFusionAuthToken(): Result<FusionAuthToken>

    suspend fun changePassword(oldPassword: String, newPassword: String): Result<Unit>

    suspend fun onboardingCompleted(): Result<Unit>

    suspend fun bindSocial(platform: String, oauthCode: String, state: String?): Result<User>

    suspend fun logoutRemote(): Result<Unit>

    suspend fun deleteAccountRemote(): Result<Unit>
}
