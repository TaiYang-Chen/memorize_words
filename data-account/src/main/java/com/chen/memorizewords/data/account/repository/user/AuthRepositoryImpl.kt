package com.chen.memorizewords.data.account.repository.user

import com.chen.memorizewords.data.account.local.mmkv.auth.AuthLocalDataSource
import com.chen.memorizewords.data.account.mapper.toDomain
import com.chen.memorizewords.data.account.remote.user.AuthRemoteDataSource
import com.chen.memorizewords.data.account.remoteapi.api.auth.BindSocialRequest
import com.chen.memorizewords.data.account.remoteapi.api.auth.ChangePasswordRequest
import com.chen.memorizewords.data.account.remoteapi.api.auth.LoginRequest
import com.chen.memorizewords.data.account.remoteapi.api.auth.RegisterRequest
import com.chen.memorizewords.data.account.remoteapi.api.auth.SendEmailCodeRequest
import com.chen.memorizewords.domain.account.model.AuthIdentifierType
import com.chen.memorizewords.domain.account.model.AuthLoginResult
import com.chen.memorizewords.domain.account.model.FusionAuthToken
import com.chen.memorizewords.domain.account.model.user.SmsCodeMeta
import com.chen.memorizewords.domain.account.model.user.User
import com.chen.memorizewords.domain.account.repository.LocalAccountRepository
import com.chen.memorizewords.domain.account.repository.user.AuthRepository
import com.chen.memorizewords.domain.account.time.ClockProvider
import com.chen.memorizewords.domain.account.usecase.user.LoginError
import com.chen.memorizewords.core.network.remote.HttpStatusException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class AuthRepositoryImpl @Inject constructor(
    private val remote: AuthRemoteDataSource,
    private val local: AuthLocalDataSource,
    private val clockProvider: ClockProvider
) : AuthRepository {
    override suspend fun loginByPassword(
        identifier: String,
        identifierType: AuthIdentifierType,
        password: String,
        cancelDeletion: Boolean
    ): Result<AuthLoginResult> = runCatching {
        withContext(Dispatchers.IO) {
            val request = LoginRequest(
                loginMethod = "password",
                identifier = identifier,
                identifierType = identifierType.wireValue,
                password = password,
                cancelDeletion = cancelDeletion
            )
            remote.login(request).getOrMapAccountDeletionPending().toDomain(clockProvider.nowEpochMillis())
        }
    }

    override suspend fun sendEmailCode(email: String, scene: String): Result<SmsCodeMeta> = runCatching {
        withContext(Dispatchers.IO) {
            remote.sendEmailCode(SendEmailCodeRequest(email = email, scene = scene)).getOrThrow().toDomain()
        }
    }

    override suspend fun loginByEmailCode(
        email: String,
        code: String,
        cancelDeletion: Boolean
    ): Result<AuthLoginResult> = runCatching {
        withContext(Dispatchers.IO) {
            val request = LoginRequest(
                loginMethod = "email_code",
                email = email,
                emailCode = code,
                cancelDeletion = cancelDeletion
            )
            remote.login(request).getOrMapAccountDeletionPending().toDomain(clockProvider.nowEpochMillis())
        }
    }

    override suspend fun loginByPhoneCode(
        phone: String,
        verifyToken: String,
        cancelDeletion: Boolean
    ): Result<AuthLoginResult> = runCatching {
        withContext(Dispatchers.IO) {
            val request = LoginRequest(
                loginMethod = "phone_code",
                phone = phone,
                verifyToken = verifyToken,
                cancelDeletion = cancelDeletion
            )
            remote.login(request).getOrMapAccountDeletionPending().toDomain(clockProvider.nowEpochMillis())
        }
    }

    override suspend fun registerByAccount(
        account: String,
        password: String
    ): Result<AuthLoginResult> = runCatching {
        withContext(Dispatchers.IO) {
            val request = RegisterRequest(
                registerMethod = "account_password",
                account = account,
                password = password
            )
            remote.register(request).getOrMapRegistrationFailure().toDomain(clockProvider.nowEpochMillis())
        }
    }

    override suspend fun registerByEmailCode(
        email: String,
        emailCode: String
    ): Result<AuthLoginResult> = runCatching {
        withContext(Dispatchers.IO) {
            val request = RegisterRequest(
                registerMethod = "email_code",
                email = email,
                emailCode = emailCode
            )
            remote.register(request).getOrThrow().toDomain(clockProvider.nowEpochMillis())
        }
    }

    override suspend fun registerByPhoneCode(
        phone: String,
        verifyToken: String
    ): Result<AuthLoginResult> = runCatching {
        withContext(Dispatchers.IO) {
            val request = RegisterRequest(
                registerMethod = "phone_code",
                phone = phone,
                verifyToken = verifyToken
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

    private fun <T> Result<T>.getOrMapAccountDeletionPending(): T {
        return getOrElse { failure ->
            if (failure is HttpStatusException && failure.code == 409) {
                throw LoginError.AccountDeletionPending(failure.message)
            }
            throw failure
        }
    }

    private fun <T> Result<T>.getOrMapRegistrationFailure(): T {
        return getOrElse { failure ->
            if (failure is HttpStatusException) {
                throw when (failure.businessCode) {
                    "AUTH_REGISTER_RATE_LIMITED" ->
                        LoginError.RegistrationRateLimited(failure.retryAfterSeconds)
                    "AUTH_REGISTER_VERIFICATION_REQUIRED" ->
                        LoginError.RegistrationVerificationRequired(failure.resetAtMs)
                    "AUTH_REGISTER_CAPACITY_BUSY", "SECURITY_PROTECTION_SERVICE_UNAVAILABLE" ->
                        LoginError.RegistrationBusy()
                    else -> failure
                }
            }
            throw failure
        }
    }
}
