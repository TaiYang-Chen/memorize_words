package com.chen.memorizewords.data.account.repository.user

import com.chen.memorizewords.data.account.local.mmkv.auth.AuthLocalDataSource
import com.chen.memorizewords.data.account.mapper.toDomain
import com.chen.memorizewords.data.account.remote.user.AuthRemoteDataSource
import com.chen.memorizewords.data.account.remoteapi.api.auth.BindSocialRequest
import com.chen.memorizewords.data.account.remoteapi.api.auth.ChangePasswordRequest
import com.chen.memorizewords.data.account.remoteapi.api.auth.FusionLoginRequest
import com.chen.memorizewords.data.account.remoteapi.api.auth.FusionRegisterRequest
import com.chen.memorizewords.data.account.remoteapi.api.auth.LoginRequest
import com.chen.memorizewords.data.account.remoteapi.api.auth.RegisterRequest
import com.chen.memorizewords.data.account.remoteapi.api.auth.SendEmailCodeRequest
import com.chen.memorizewords.domain.account.model.AuthLoginResult
import com.chen.memorizewords.domain.account.model.FusionAuthToken
import com.chen.memorizewords.domain.account.model.user.SmsCodeMeta
import com.chen.memorizewords.domain.account.model.user.User
import com.chen.memorizewords.domain.account.repository.LocalAccountRepository
import com.chen.memorizewords.domain.account.repository.user.AuthRepository
import com.chen.memorizewords.domain.account.time.ClockProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class AuthRepositoryImpl @Inject constructor(
    private val remote: AuthRemoteDataSource,
    private val local: AuthLocalDataSource,
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
                password = password
            )
            remote.login(request).getOrThrow().toDomain(clockProvider.nowEpochMillis())
        }
    }

    override suspend fun sendEmailCode(email: String, scene: String): Result<SmsCodeMeta> = runCatching {
        withContext(Dispatchers.IO) {
            remote.sendEmailCode(SendEmailCodeRequest(email = email, scene = scene)).getOrThrow().toDomain()
        }
    }

    override suspend fun loginByEmailCode(email: String, code: String): Result<AuthLoginResult> = runCatching {
        withContext(Dispatchers.IO) {
            val request = LoginRequest(
                loginMethod = "email_code",
                email = email,
                emailCode = code
            )
            remote.login(request).getOrThrow().toDomain(clockProvider.nowEpochMillis())
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
        email: String,
        emailCode: String,
        password: String
    ): Result<AuthLoginResult> = runCatching {
        withContext(Dispatchers.IO) {
            val request = RegisterRequest(
                email = email,
                emailCode = emailCode,
                password = password
            )
            remote.register(request).getOrThrow().toDomain(clockProvider.nowEpochMillis())
        }
    }

    override suspend fun getFusionAuthToken(): Result<FusionAuthToken> = runCatching {
        withContext(Dispatchers.IO) {
            val dto = remote.getFusionAuthToken().getOrThrow()
            FusionAuthToken(
                authToken = dto.authToken,
                schemeCode = dto.schemeCode,
                expiresIn = dto.expiresIn
            )
        }
    }

    override suspend fun loginByFusionVerifyToken(verifyToken: String): Result<AuthLoginResult> = runCatching {
        withContext(Dispatchers.IO) {
            remote.fusionLogin(FusionLoginRequest(verifyToken))
                .getOrThrow()
                .toDomain(clockProvider.nowEpochMillis())
        }
    }

    override suspend fun registerByFusionVerifyToken(
        verifyToken: String,
        password: String
    ): Result<AuthLoginResult> = runCatching {
        withContext(Dispatchers.IO) {
            remote.fusionRegister(FusionRegisterRequest(verifyToken, password))
                .getOrThrow()
                .toDomain(clockProvider.nowEpochMillis())
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

    override suspend fun onboardingCompleted(): Result<Unit> {
        return withContext(Dispatchers.IO) {
            local.onboardingCompleted()
            remote.onboardingCompleted()
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
