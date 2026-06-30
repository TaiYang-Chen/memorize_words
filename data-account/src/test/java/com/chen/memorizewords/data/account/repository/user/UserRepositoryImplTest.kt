package com.chen.memorizewords.data.account.repository.user

import com.chen.memorizewords.data.account.local.avatar.AvatarLocalDataSource
import com.chen.memorizewords.data.account.local.mmkv.auth.AuthLocalDataSource
import com.chen.memorizewords.data.account.remote.user.AuthRemoteDataSource
import com.chen.memorizewords.data.account.remoteapi.api.auth.AvatarUploadDto
import com.chen.memorizewords.data.account.remoteapi.api.auth.BindEmailRequest
import com.chen.memorizewords.data.account.remoteapi.api.auth.BindPhoneRequest
import com.chen.memorizewords.data.account.remoteapi.api.auth.BindSocialRequest
import com.chen.memorizewords.data.account.remoteapi.api.auth.ChangePasswordRequest
import com.chen.memorizewords.data.account.remoteapi.api.auth.FusionAuthTokenDto
import com.chen.memorizewords.data.account.remoteapi.api.auth.FusionRegisterRequest
import com.chen.memorizewords.data.account.remoteapi.api.auth.LoginRequest
import com.chen.memorizewords.data.account.remoteapi.api.auth.ProfilePatchRequest
import com.chen.memorizewords.data.account.remoteapi.api.auth.RegisterRequest
import com.chen.memorizewords.data.account.remoteapi.api.auth.SendEmailCodeRequest
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
            val repository = UserRepositoryImpl(remote, local, FakeAvatarLocalDataSource())

            val result = repository.bindEmail("new@example.com", "123456")

            assertTrue(result.isSuccess)
            assertEquals(BindEmailRequest("new@example.com", "123456"), remote.capturedBindEmailRequest)
            assertEquals("new@example.com", result.getOrThrow().email)
            assertEquals("new@example.com", local.getUser()?.email)
            assertEquals(true, local.getUser()?.onboardingCompleted)
        }
    }

    @Test
    fun `bindPhone sends fusion token and saves returned profile locally`() {
        runBlocking {
            val local = FakeAuthLocalDataSource(
                user(
                    email = "old@example.com",
                    onboardingCompleted = true
                )
            )
            val remote = FakeAuthRemoteDataSource(
                bindPhoneProfile = ProfileDto(
                    userId = 7L,
                    email = "old@example.com",
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
            val repository = UserRepositoryImpl(remote, local, FakeAvatarLocalDataSource())

            val result = repository.bindPhoneByFusionVerifyToken("verify-token")

            assertTrue(result.isSuccess)
            assertEquals(BindPhoneRequest("verify-token"), remote.capturedBindPhoneRequest)
            assertEquals("13800000000", result.getOrThrow().phone)
            assertEquals("13800000000", local.getUser()?.phone)
            assertEquals(true, local.getUser()?.onboardingCompleted)
        }
    }

    @Test
    fun `profile update keeps existing local avatar path`() {
        runBlocking {
            val local = FakeAuthLocalDataSource(
                user(
                    email = "old@example.com",
                    onboardingCompleted = true,
                    avatarUrl = "https://example.com/avatar.jpg",
                    localAvatarPath = "/local/avatar.jpg"
                )
            )
            val remote = FakeAuthRemoteDataSource(
                updateProfile = ProfileDto(
                    userId = 7L,
                    email = "old@example.com",
                    nickname = "New name",
                    gender = null,
                    avatarUrl = "https://example.com/avatar.jpg",
                    phone = null,
                    qq = null,
                    wechat = null,
                    emailVerified = true,
                    onboardingCompleted = null
                )
            )
            val repository = UserRepositoryImpl(remote, local, FakeAvatarLocalDataSource())

            val result = repository.updateNickname("New name")

            assertTrue(result.isSuccess)
            assertEquals("/local/avatar.jpg", result.getOrThrow().localAvatarPath)
            assertEquals("/local/avatar.jpg", local.getUser()?.localAvatarPath)
        }
    }

    @Test
    fun `updateAvatar caches uploaded image path locally`() {
        runBlocking {
            val local = FakeAuthLocalDataSource(
                user(
                    email = "old@example.com",
                    onboardingCompleted = true
                )
            )
            val avatarLocal = FakeAvatarLocalDataSource()
            val remote = FakeAuthRemoteDataSource(
                updateProfile = ProfileDto(
                    userId = 7L,
                    email = "old@example.com",
                    nickname = "Demo",
                    gender = null,
                    avatarUrl = "https://example.com/new-avatar.jpg",
                    phone = null,
                    qq = null,
                    wechat = null,
                    emailVerified = true,
                    onboardingCompleted = null
                )
            )
            val repository = UserRepositoryImpl(remote, local, avatarLocal)

            val result = repository.updateAvatar("https://example.com/new-avatar.jpg", byteArrayOf(1, 2, 3))

            assertTrue(result.isSuccess)
            assertEquals("/cached/user_7.jpg", result.getOrThrow().localAvatarPath)
            assertEquals(byteArrayOf(1, 2, 3).toList(), avatarLocal.savedBytes?.toList())
            assertEquals("/cached/user_7.jpg", local.getUser()?.localAvatarPath)
        }
    }

    private class FakeAuthRemoteDataSource(
        private val bindEmailProfile: ProfileDto? = null,
        private val bindPhoneProfile: ProfileDto? = null,
        private val updateProfile: ProfileDto? = null
    ) : AuthRemoteDataSource {
        var capturedBindEmailRequest: BindEmailRequest? = null
        var capturedBindPhoneRequest: BindPhoneRequest? = null

        override suspend fun login(loginRequest: LoginRequest): Result<LoginResponseDto> = unused()

        override suspend fun loginByWechat(loginRequest: LoginRequest): Result<LoginResponseDto> = unused()

        override suspend fun loginByQq(loginRequest: LoginRequest): Result<LoginResponseDto> = unused()

        override suspend fun sendEmailCode(
            request: SendEmailCodeRequest
        ): Result<SendSmsCodeResponseDto> = unused()

        override suspend fun register(request: RegisterRequest): Result<LoginResponseDto> = unused()

        override suspend fun getFusionAuthToken(): Result<FusionAuthTokenDto> = unused()

        override suspend fun fusionRegister(request: FusionRegisterRequest): Result<LoginResponseDto> = unused()

        override suspend fun getProfile(): Result<ProfileDto> = unused()

        override suspend fun logout(): Result<Unit> = unused()

        override suspend fun deleteAccount(): Result<Unit> = unused()

        override suspend fun changePassword(request: ChangePasswordRequest): Result<Unit> = unused()

        override suspend fun onboardingCompleted(): Result<Unit> = unused()

        override suspend fun bindSocial(request: BindSocialRequest): Result<ProfileDto> = unused()

        override suspend fun bindEmail(request: BindEmailRequest): Result<ProfileDto> {
            capturedBindEmailRequest = request
            return bindEmailProfile?.let { Result.success(it) } ?: unused()
        }

        override suspend fun bindPhone(request: BindPhoneRequest): Result<ProfileDto> {
            capturedBindPhoneRequest = request
            return bindPhoneProfile?.let { Result.success(it) } ?: unused()
        }

        override suspend fun uploadAvatar(file: MultipartBody.Part): Result<AvatarUploadDto> = unused()

        override suspend fun update(request: ProfilePatchRequest): Result<ProfileDto> {
            return updateProfile?.let { Result.success(it) } ?: unused()
        }

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

        override fun onboardingCompleted() {
            userState.value = userState.value?.copy(onboardingCompleted = true)
        }
    }

    private class FakeAvatarLocalDataSource : AvatarLocalDataSource {
        var savedBytes: ByteArray? = null

        override fun saveAvatar(userId: Long, imageBytes: ByteArray): String {
            savedBytes = imageBytes
            return "/cached/user_$userId.jpg"
        }

        override fun deleteAvatar(path: String?) = Unit
    }

    private fun user(
        email: String,
        onboardingCompleted: Boolean,
        avatarUrl: String? = null,
        localAvatarPath: String? = null
    ): User = User(
        userId = 7L,
        email = email,
        nickname = "Demo",
        gender = null,
        avatarUrl = avatarUrl,
        phone = null,
        qq = null,
        wechat = null,
        emailVerified = true,
        onboardingCompleted = onboardingCompleted,
        localAvatarPath = localAvatarPath
    )
}
