package com.chen.memorizewords.data.account.repository.user

import com.chen.memorizewords.core.network.remote.HttpStatusException
import com.chen.memorizewords.data.account.local.mmkv.auth.AuthLocalDataSource
import com.chen.memorizewords.data.account.remote.user.AuthRemoteDataSource
import com.chen.memorizewords.data.account.remoteapi.api.auth.AvatarUploadDto
import com.chen.memorizewords.data.account.remoteapi.api.auth.BindEmailRequest
import com.chen.memorizewords.data.account.remoteapi.api.auth.BindPhoneRequest
import com.chen.memorizewords.data.account.remoteapi.api.auth.BindSocialRequest
import com.chen.memorizewords.data.account.remoteapi.api.auth.ChangePasswordRequest
import com.chen.memorizewords.data.account.remoteapi.api.auth.FusionAuthTokenDto
import com.chen.memorizewords.data.account.remoteapi.api.auth.LoginRequest
import com.chen.memorizewords.data.account.remoteapi.api.auth.ProfilePatchRequest
import com.chen.memorizewords.data.account.remoteapi.api.auth.RegisterRequest
import com.chen.memorizewords.data.account.remoteapi.api.auth.SendEmailCodeRequest
import com.chen.memorizewords.data.account.remoteapi.dto.LoginResponseDto
import com.chen.memorizewords.data.account.remoteapi.dto.ProfileDto
import com.chen.memorizewords.data.account.remoteapi.dto.SendSmsCodeResponseDto
import com.chen.memorizewords.data.account.remoteapi.dto.UserDto
import com.chen.memorizewords.domain.account.model.AuthIdentifierType
import com.chen.memorizewords.domain.account.model.user.User
import com.chen.memorizewords.domain.account.time.ClockProvider
import com.chen.memorizewords.domain.account.usecase.user.LoginError
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import okhttp3.MultipartBody
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AuthRepositoryImplTest {

    @Test
    fun `password login sends identifier request`() = runBlocking {
        val remote = FakeAuthRemoteDataSource()
        val repository = AuthRepositoryImpl(remote, FakeAuthLocalDataSource(), FakeClockProvider())

        repository.loginByPassword(
            identifier = "demo@example.com",
            identifierType = AuthIdentifierType.EMAIL,
            password = "secret",
            cancelDeletion = true
        ).getOrThrow()

        assertEquals(
            LoginRequest(
                loginMethod = "password",
                identifierType = "email",
                identifier = "demo@example.com",
                password = "secret",
                cancelDeletion = true
            ),
            remote.capturedLoginRequest
        )
    }

    @Test
    fun `email and phone code login send expected requests`() = runBlocking {
        val remote = FakeAuthRemoteDataSource()
        val repository = AuthRepositoryImpl(remote, FakeAuthLocalDataSource(), FakeClockProvider())

        repository.loginByEmailCode("demo@example.com", "123456", cancelDeletion = false).getOrThrow()
        assertEquals(
            LoginRequest(
                loginMethod = "email_code",
                email = "demo@example.com",
                emailCode = "123456"
            ),
            remote.capturedLoginRequest
        )

        repository.loginByPhoneCode("13800000000", "verify-token", cancelDeletion = true).getOrThrow()
        assertEquals(
            LoginRequest(
                loginMethod = "phone_code",
                phone = "13800000000",
                verifyToken = "verify-token",
                cancelDeletion = true
            ),
            remote.capturedLoginRequest
        )
    }

    @Test
    fun `register methods send expected requests`() = runBlocking {
        val remote = FakeAuthRemoteDataSource()
        val repository = AuthRepositoryImpl(remote, FakeAuthLocalDataSource(), FakeClockProvider())

        repository.registerByAccount("alice", "secret").getOrThrow()
        assertEquals(
            RegisterRequest(
                registerMethod = "account_password",
                account = "alice",
                password = "secret"
            ),
            remote.capturedRegisterRequest
        )

        repository.registerByEmailCode("demo@example.com", "123456").getOrThrow()
        assertEquals(
            RegisterRequest(
                registerMethod = "email_code",
                email = "demo@example.com",
                emailCode = "123456"
            ),
            remote.capturedRegisterRequest
        )

        repository.registerByPhoneCode("13800000000", "verify-token").getOrThrow()
        assertEquals(
            RegisterRequest(
                registerMethod = "phone_code",
                phone = "13800000000",
                verifyToken = "verify-token"
            ),
            remote.capturedRegisterRequest
        )
    }

    @Test
    fun `login conflict maps to account deletion pending`() = runBlocking {
        val remote = FakeAuthRemoteDataSource(
            loginResult = Result.failure(HttpStatusException(409, "pending deletion"))
        )
        val repository = AuthRepositoryImpl(remote, FakeAuthLocalDataSource(), FakeClockProvider())

        val result = repository.loginByEmailCode("demo@example.com", "123456")

        assertTrue(result.isFailure)
        assertIs<LoginError.AccountDeletionPending>(result.exceptionOrNull())
        assertEquals("pending deletion", result.exceptionOrNull()?.message)
    }

    private class FakeAuthRemoteDataSource(
        private val loginResult: Result<LoginResponseDto> = Result.success(loginResponse()),
        private val registerResult: Result<LoginResponseDto> = Result.success(loginResponse())
    ) : AuthRemoteDataSource {
        var capturedLoginRequest: LoginRequest? = null
        var capturedRegisterRequest: RegisterRequest? = null

        override suspend fun login(loginRequest: LoginRequest): Result<LoginResponseDto> {
            capturedLoginRequest = loginRequest
            return loginResult
        }

        override suspend fun sendEmailCode(
            request: SendEmailCodeRequest
        ): Result<SendSmsCodeResponseDto> = unused()

        override suspend fun register(request: RegisterRequest): Result<LoginResponseDto> {
            capturedRegisterRequest = request
            return registerResult
        }

        override suspend fun getFusionAuthToken(): Result<FusionAuthTokenDto> = unused()

        override suspend fun getProfile(): Result<ProfileDto> = unused()

        override suspend fun logout(): Result<Unit> = unused()

        override suspend fun deleteAccount(): Result<Unit> = unused()

        override suspend fun changePassword(request: ChangePasswordRequest): Result<Unit> = unused()

        override suspend fun onboardingCompleted(): Result<Unit> = unused()

        override suspend fun bindSocial(request: BindSocialRequest): Result<ProfileDto> = unused()

        override suspend fun bindEmail(request: BindEmailRequest): Result<ProfileDto> = unused()

        override suspend fun bindPhone(request: BindPhoneRequest): Result<ProfileDto> = unused()

        override suspend fun uploadAvatar(file: MultipartBody.Part): Result<AvatarUploadDto> = unused()

        override suspend fun update(request: ProfilePatchRequest): Result<ProfileDto> = unused()

        private fun <T> unused(): Result<T> =
            Result.failure(AssertionError("Unexpected remote call"))
    }

    private class FakeAuthLocalDataSource : AuthLocalDataSource {
        private val userState = MutableStateFlow<User?>(null)

        override fun getUser(): User? = userState.value

        override fun getUserFlow(): Flow<User?> = userState

        override fun getUserId(): Long? = userState.value?.userId

        override fun saveUser(user: User) {
            userState.value = user
        }

        override fun clearUser() {
            userState.value = null
        }

        override fun clear() {
            userState.value = null
        }

        override fun onboardingCompleted() = Unit
    }

    private class FakeClockProvider : ClockProvider {
        override fun nowEpochMillis(): Long = 1_000L
    }

    private companion object {
        fun loginResponse(): LoginResponseDto =
            LoginResponseDto(
                token = "access",
                refreshToken = "refresh",
                tokenType = "Bearer",
                user = UserDto(
                    id = 7L,
                    email = "demo@example.com",
                    nickname = "Demo",
                    gender = null,
                    avatarUrl = null,
                    phone = "13800000000",
                    qq = null,
                    wechat = null,
                    emailVerified = true,
                    onboardingCompleted = true
                ),
                expiresIn = 60L,
                refreshTokenExpiresIn = 600L
            )
    }
}
