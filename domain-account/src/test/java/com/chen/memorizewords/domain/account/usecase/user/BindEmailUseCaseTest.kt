package com.chen.memorizewords.domain.account.usecase.user

import com.chen.memorizewords.domain.account.model.user.User
import com.chen.memorizewords.domain.account.repository.user.UserRepository
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class BindEmailUseCaseTest {

    @Test
    fun `blank email returns empty email error`() {
        runBlocking {
            val result = BindEmailUseCase(FakeUserRepository())("", "123456")

            assertTrue(result.isFailure)
            assertIs<LoginError.EmptyEmail>(result.exceptionOrNull())
        }
    }

    @Test
    fun `blank code returns empty sms code error`() {
        runBlocking {
            val result = BindEmailUseCase(FakeUserRepository())("demo@example.com", "")

            assertTrue(result.isFailure)
            assertIs<LoginError.EmptySmsCode>(result.exceptionOrNull())
        }
    }

    @Test
    fun `valid input is trimmed before binding email`() {
        runBlocking {
            val repo = FakeUserRepository()
            val result = BindEmailUseCase(repo)(" demo@example.com ", " 123456 ")

            assertTrue(result.isSuccess)
            assertEquals("demo@example.com", repo.boundEmail)
            assertEquals("123456", repo.boundEmailCode)
            assertEquals("demo@example.com", result.getOrThrow().email)
        }
    }

    private class FakeUserRepository : UserRepository {
        var boundEmail: String? = null
        var boundEmailCode: String? = null

        override suspend fun updateNickname(nickname: String): Result<User> = unused()

        override suspend fun updateGender(gender: String): Result<User> = unused()

        override suspend fun updatePhone(phone: String): Result<User> = unused()

        override suspend fun bindEmail(email: String, emailCode: String): Result<User> {
            boundEmail = email
            boundEmailCode = emailCode
            return Result.success(user(email = email))
        }

        override suspend fun updateWechat(wechat: String): Result<User> = unused()

        override suspend fun updateQQ(qq: String): Result<User> = unused()

        override suspend fun uploadAvatar(imageBytes: ByteArray): Result<String> = unused()

        override suspend fun updateAvatar(avatarUrl: String): Result<User> = unused()

        private fun user(email: String): User = User(
            userId = 1L,
            email = email,
            nickname = "Demo",
            gender = null,
            avatarUrl = null,
            phone = null,
            qq = null,
            wechat = null,
            emailVerified = true,
            onboardingCompleted = true
        )

        private fun <T> unused(): Result<T> =
            Result.failure(AssertionError("Unexpected repository call"))
    }
}
