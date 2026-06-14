package com.chen.memorizewords.data.account.repository.user

import com.chen.memorizewords.data.account.local.mmkv.auth.AuthLocalDataSource
import com.chen.memorizewords.data.account.remote.user.AuthRemoteDataSource
import com.chen.memorizewords.data.account.remoteapi.api.auth.AvatarUploadDto
import com.chen.memorizewords.data.account.remoteapi.api.auth.BindEmailRequest
import com.chen.memorizewords.data.account.remoteapi.api.auth.BindSocialRequest
import com.chen.memorizewords.data.account.remoteapi.api.auth.ChangePasswordRequest
import com.chen.memorizewords.data.account.remoteapi.api.auth.LoginRequest
import com.chen.memorizewords.data.account.remoteapi.api.auth.ProfilePatchRequest
import com.chen.memorizewords.data.account.remoteapi.api.auth.RegisterRequest
import com.chen.memorizewords.data.account.remoteapi.api.auth.SendEmailCodeRequest
import com.chen.memorizewords.data.account.remoteapi.api.auth.SendSmsCodeRequest
import com.chen.memorizewords.data.account.remoteapi.dto.LoginResponseDto
import com.chen.memorizewords.data.account.remoteapi.dto.ProfileDto
import com.chen.memorizewords.data.account.remoteapi.dto.SendSmsCodeResponseDto
import com.chen.memorizewords.domain.account.model.user.User
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import okhttp3.MultipartBody
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UserRepositoryImplTest {

    @Test
    fun `bindEmail sends request and saves returned profile locally`() {
        runBlocking {
            val local = FakeAuthLocalDataSource(
                user(
                    email = "old@example.com",
                    onboardingCompleted = true
                )
            )
            val remote = FakeAuthRemoteDataSource(
                bindEmailProfile = ProfileDto(
                    userId = 7L,
                    email = "new@example.com",
                    nickname = "Demo",
                    gender = null,
                    avatarUrl = null,
                    phone = "13800000000",
                    qq = null,
                    wechat = null,
                    emailVerified = true,
                    onboardingCompleted = null
                )
            )
            val repository = UserRepositoryImpl(remote, local)

            val result = repository.bindEmail("new@example.com", "123456")

            assertTrue(result.isSuccess)
            assertEquals(BindEmailRequest("new@example.com", "123456"), remote.capturedBindEmailRequest)
            assertEquals("new@example.com", result.getOrThrow().email)
            assertEquals("new@example.com", local.getUser()?.email)
            assertEquals(true, local.getUser()?.onboardingCompleted)
        }
    }

    private class FakeAuthRemoteDataSource(
        private val bindEmailProfile: ProfileDto
    ) : AuthRemoteDataSource {
        var capturedBindEmailRequest: BindEmailRequest? = null

        override suspend fun login(loginRequest: LoginRequest): Result<LoginResponseDto> = unused()

        override suspend fun loginBySms(loginRequest: LoginRequest): Result<LoginResponseDto> = unused()

        override suspend fun loginByWechat(loginRequest: LoginRequest): Result<LoginResponseDto> = unused()

        override suspend fun loginByQq(loginRequest: LoginRequest): Result<LoginResponseDto> = unused()

        override suspend fun sendLoginSmsCode(
            request: SendSmsCodeRequest
        ): Result<SendSmsCodeResponseDto> = unused()

        override suspend fun sendEmailCode(
            request: SendEmailCodeRequest
        ): Result<SendSmsCodeResponseDto> = unused()

        override suspend fun register(request: RegisterRequest): Result<LoginResponseDto> = unused()

        override suspend fun getProfile(): Result<ProfileDto> = unused()

        override suspend fun logout(): Result<Unit> = unused()

        override suspend fun deleteAccount(): Result<Unit> = unused()

        override suspend fun changePassword(request: ChangePasswordRequest): Result<Unit> = unused()

        override suspend fun bindSocial(request: BindSocialRequest): Result<ProfileDto> = unused()

        override suspend fun bindEmail(request: BindEmailRequest): Result<ProfileDto> {
            capturedBindEmailRequest = request
            return Result.success(bindEmailProfile)
        }

        override suspend fun uploadAvatar(file: MultipartBody.Part): Result<AvatarUploadDto> = unused()

        override suspend fun update(request: ProfilePatchRequest): Result<ProfileDto> = unused()

        private fun <T> unused(): Result<T> =
            Result.failure(AssertionError("Unexpected remote call"))
    }

    private class FakeAuthLocalDataSource(initialUser: User?) : AuthLocalDataSource {
        private val userState = MutableStateFlow(initialUser)

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
    }

    private fun user(
        email: String,
        onboardingCompleted: Boolean
    ): User = User(
        userId = 7L,
        email = email,
        nickname = "Demo",
        gender = null,
        avatarUrl = null,
        phone = null,
        qq = null,
        wechat = null,
        emailVerified = true,
        onboardingCompleted = onboardingCompleted
    )
}
