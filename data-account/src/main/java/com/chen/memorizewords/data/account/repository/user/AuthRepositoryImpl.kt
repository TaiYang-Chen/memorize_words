package com.chen.memorizewords.data.account.repository.user

import com.chen.memorizewords.data.account.mapper.toDomain
import com.chen.memorizewords.data.account.remote.user.AuthRemoteDataSource
import com.chen.memorizewords.data.account.remoteapi.api.auth.BindSocialRequest
import com.chen.memorizewords.data.account.remoteapi.api.auth.ChangePasswordRequest
import com.chen.memorizewords.data.account.remoteapi.api.auth.LoginRequest
import com.chen.memorizewords.data.account.remoteapi.api.auth.RegisterRequest
import com.chen.memorizewords.data.account.remoteapi.api.auth.SendSmsCodeRequest
import com.chen.memorizewords.domain.account.model.AuthLoginResult
import com.chen.memorizewords.domain.account.model.user.SmsCodeMeta
import com.chen.memorizewords.domain.account.model.user.User
import com.chen.memorizewords.domain.account.repository.user.AuthRepository
import com.chen.memorizewords.domain.account.time.ClockProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class AuthRepositoryImpl @Inject constructor(
    private val remote: AuthRemoteDataSource,
    private val clockProvider: ClockProvider
) : AuthRepository {
    override suspend fun loginByPassword(
        phoneNumber: String,
        password: String
    ): Result<AuthLoginResult> = runCatching {
        withContext(Dispatchers.IO) {
            val request = LoginRequest(
                loginMethod = "password",
                emailOrPhone = phoneNumber,
                phone = phoneNumber,
                password = password
            )
            remote.login(request).getOrThrow().toDomain(clockProvider.nowEpochMillis())
        }
    }

    override suspend fun sendLoginSmsCode(phone: String): Result<SmsCodeMeta> = runCatching {
        withContext(Dispatchers.IO) {
            remote.sendLoginSmsCode(SendSmsCodeRequest(phone = phone)).getOrThrow().toDomain()
        }
    }

    override suspend fun loginBySms(phone: String, code: String): Result<AuthLoginResult> = runCatching {
        withContext(Dispatchers.IO) {
            val request = LoginRequest(
                loginMethod = "sms",
                phone = phone,
                smsCode = code
            )
            remote.loginBySms(request).getOrThrow().toDomain(clockProvider.nowEpochMillis())
        }
    }

    override suspend fun loginByWechat(oauthCode: String, state: String?): Result<AuthLoginResult> = runCatching {
        withContext(Dispatchers.IO) {
            val request = LoginRequest(
                loginMethod = "wechat",
                oauthCode = oauthCode,
                platform = "wechat",
                state = state
            )
            remote.loginByWechat(request).getOrThrow().toDomain(clockProvider.nowEpochMillis())
        }
    }

    override suspend fun loginByQq(oauthCode: String, state: String?): Result<AuthLoginResult> = runCatching {
        withContext(Dispatchers.IO) {
            val request = LoginRequest(
                loginMethod = "qq",
                oauthCode = oauthCode,
                platform = "qq",
                state = state
            )
            remote.loginByQq(request).getOrThrow().toDomain(clockProvider.nowEpochMillis())
        }
    }

    override suspend fun register(
        phoneNumber: String,
        password: String
    ): Result<AuthLoginResult> = runCatching {
        withContext(Dispatchers.IO) {
            val request = RegisterRequest(
                phone = phoneNumber,
                password = password
            )
            remote.register(request).getOrThrow().toDomain(clockProvider.nowEpochMillis())
        }
    }

    override suspend fun changePassword(oldPassword: String, newPassword: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            remote.changePassword(
                ChangePasswordRequest(
                    oldPassword = oldPassword,
                    newPassword = newPassword
                )
            )
        }
    }

    override suspend fun bindSocial(
        platform: String,
        oauthCode: String,
        state: String?
    ): Result<User> = runCatching {
        withContext(Dispatchers.IO) {
            remote.bindSocial(
                BindSocialRequest(
                    platform = platform,
                    oauthCode = oauthCode,
                    state = state
                )
            ).getOrThrow().toDomain()
        }
    }

    override suspend fun logoutRemote(): Result<Unit> = runCatching {
        withContext(Dispatchers.IO) {
            remote.logout().getOrThrow()
        }
    }

    override suspend fun deleteAccountRemote(): Result<Unit> = runCatching {
        withContext(Dispatchers.IO) {
            remote.deleteAccount().getOrThrow()
        }
    }
}

