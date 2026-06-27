package com.chen.memorizewords.domain.account.usecase.user

import com.chen.memorizewords.domain.account.model.user.User
import com.chen.memorizewords.domain.account.repository.user.UserRepository
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class BindPhoneUseCaseTest {

    @Test
    fun `blank verify token returns empty oauth code error`() {
        runBlocking {
            val result = BindPhoneUseCase(FakeUserRepository())("")

            assertTrue(result.isFailure)
            assertIs<LoginError.EmptyOauthCode>(result.exceptionOrNull())
        }
    }

    @Test
    fun `valid verify token is trimmed before binding phone`() {
        runBlocking {
            val repo = FakeUserRepository()
            val result = BindPhoneUseCase(repo)(" verify-token ")

            assertTrue(result.isSuccess)
            assertEquals("verify-token", repo.boundVerifyToken)
            assertEquals("13800000000", result.getOrThrow().phone)
        }
    }

    private class FakeUserRepository : UserRepository {
        var boundVerifyToken: String? = null

        override suspend fun updateNickname(nickname: String): Result<User> = unused()

        override suspend fun updateGender(gender: String): Result<User> = unused()

        override suspend fun updatePhone(phone: String): Result<User> = unused()

        override suspend fun bindPhoneByFusionVerifyToken(verifyToken: String): Result<User> {
            boundVerifyToken = verifyToken
            return Result.success(user())
        }

        override suspend fun bindEmail(email: String, emailCode: String): Result<User> = unused()

        override suspend fun updateWechat(wechat: String): Result<User> = unused()

        override suspend fun updateQQ(qq: String): Result<User> = unused()

        override suspend fun uploadAvatar(imageBytes: ByteArray): Result<String> = unused()

        override suspend fun updateAvatar(avatarUrl: String, imageBytes: ByteArray): Result<User> = unused()

        override suspend fun cacheLoadedAvatar(imageBytes: ByteArray, avatarUrl: String?): Result<User> = unused()

        private fun user(): User = User(
            userId = 1L,
            email = "demo@example.com",
            nickname = "Demo",
            gender = null,
            avatarUrl = null,
            phone = "13800000000",
            qq = null,
            wechat = null,
            emailVerified = true,
            onboardingCompleted = true
        )

        private fun <T> unused(): Result<T> =
            Result.failure(AssertionError("Unexpected repository call"))
    }
}
