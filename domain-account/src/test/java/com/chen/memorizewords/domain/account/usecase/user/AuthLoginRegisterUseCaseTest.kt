package com.chen.memorizewords.domain.account.usecase.user

import com.chen.memorizewords.domain.account.model.AccountSession
import com.chen.memorizewords.domain.account.model.AuthIdentifierType
import com.chen.memorizewords.domain.account.model.AuthLoginResult
import com.chen.memorizewords.domain.account.model.FusionAuthToken
import com.chen.memorizewords.domain.account.model.user.SmsCodeMeta
import com.chen.memorizewords.domain.account.model.user.User
import com.chen.memorizewords.domain.account.repository.AccountSessionRepository
import com.chen.memorizewords.domain.account.repository.LocalAccountRepository
import com.chen.memorizewords.domain.account.repository.LoginBootstrapApplier
import com.chen.memorizewords.domain.account.repository.user.AuthRepository
import com.chen.memorizewords.domain.sync.model.LearningPrerequisitesSnapshot
import com.chen.memorizewords.domain.sync.model.LoginBootstrap
import com.chen.memorizewords.domain.sync.model.PostLoginBootstrapState
import com.chen.memorizewords.domain.sync.model.SyncBannerState
import com.chen.memorizewords.domain.sync.model.SyncPendingRecord
import com.chen.memorizewords.domain.sync.repository.SyncRepository
import com.chen.memorizewords.domain.sync.service.SyncFacade
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AuthLoginRegisterUseCaseTest {

    @Test
    fun `password login identifies account email and phone`() {
        runBlocking {
            val repo = FakeAuthRepository()
            val useCase = LoginUseCase(repo, createLoginCompletionHandler())

            useCase(" alice ", "password").getOrThrow()
            assertEquals("alice", repo.passwordIdentifier)
            assertEquals(AuthIdentifierType.ACCOUNT, repo.passwordIdentifierType)

            useCase("demo@example.com", "password").getOrThrow()
            assertEquals("demo@example.com", repo.passwordIdentifier)
            assertEquals(AuthIdentifierType.EMAIL, repo.passwordIdentifierType)

            useCase("13800000000", "password").getOrThrow()
            assertEquals("13800000000", repo.passwordIdentifier)
            assertEquals(AuthIdentifierType.PHONE, repo.passwordIdentifierType)
        }
    }

    @Test
    fun `password login validates required fields`() {
        runBlocking {
            val useCase = LoginUseCase(FakeAuthRepository(), createLoginCompletionHandler())

            val missingIdentifier = useCase("", "password")
            val missingPassword = useCase("alice", "")

            assertTrue(missingIdentifier.isFailure)
            assertIs<LoginError.EmptyIdentifier>(missingIdentifier.exceptionOrNull())
            assertTrue(missingPassword.isFailure)
            assertIs<LoginError.EmptyPassword>(missingPassword.exceptionOrNull())
        }
    }

    @Test
    fun `account register trims account and validates password`() {
        runBlocking {
            val repo = FakeAuthRepository()
            val useCase = RegisterUseCase(repo, createLoginCompletionHandler())

            useCase(" alice ", "password").getOrThrow()
            val missingPassword = useCase("alice", "")

            assertEquals("alice", repo.registeredAccount)
            assertEquals("password", repo.registeredAccountPassword)
            assertTrue(missingPassword.isFailure)
            assertIs<LoginError.EmptyPassword>(missingPassword.exceptionOrNull())
        }
    }

    @Test
    fun `email code register trims email and code`() {
        runBlocking {
            val repo = FakeAuthRepository()
            val useCase = EmailCodeRegisterUseCase(repo, createLoginCompletionHandler())

            useCase(" demo@example.com ", " 123456 ").getOrThrow()

            assertEquals("demo@example.com", repo.registeredEmail)
            assertEquals("123456", repo.registeredEmailCode)
        }
    }

    @Test
    fun `phone code login validates phone and trims verify token`() {
        runBlocking {
            val repo = FakeAuthRepository()
            val useCase = PhoneCodeLoginUseCase(repo, createLoginCompletionHandler())

            val invalidPhone = useCase("100", "verify-token")
            useCase(" 13800000000 ", " verify-token ").getOrThrow()

            assertTrue(invalidPhone.isFailure)
            assertIs<LoginError.InvalidPhone>(invalidPhone.exceptionOrNull())
            assertEquals("13800000000", repo.phoneCodeLoginPhone)
            assertEquals("verify-token", repo.phoneCodeLoginVerifyToken)
        }
    }

    @Test
    fun `phone code register trims phone and verify token`() {
        runBlocking {
            val repo = FakeAuthRepository()
            val useCase = PhoneCodeRegisterUseCase(repo, createLoginCompletionHandler())

            useCase(" 13800000000 ", " verify-token ").getOrThrow()

            assertEquals("13800000000", repo.registeredPhone)
            assertEquals("verify-token", repo.registeredPhoneVerifyToken)
        }
    }

    private class FakeAuthRepository : AuthRepository {
        var passwordIdentifier: String? = null
        var passwordIdentifierType: AuthIdentifierType? = null
        var registeredAccount: String? = null
        var registeredAccountPassword: String? = null
        var registeredEmail: String? = null
        var registeredEmailCode: String? = null
        var registeredPhone: String? = null
        var registeredPhoneVerifyToken: String? = null
        var phoneCodeLoginPhone: String? = null
        var phoneCodeLoginVerifyToken: String? = null

        override suspend fun loginByPassword(
            identifier: String,
            identifierType: AuthIdentifierType,
            password: String,
            cancelDeletion: Boolean
        ): Result<AuthLoginResult> {
            passwordIdentifier = identifier
            passwordIdentifierType = identifierType
            return Result.success(loginResult())
        }

        override suspend fun sendEmailCode(email: String, scene: String): Result<SmsCodeMeta> =
            unused()

        override suspend fun loginByEmailCode(
            email: String,
            code: String,
            cancelDeletion: Boolean
        ): Result<AuthLoginResult> = Result.success(loginResult())

        override suspend fun loginByPhoneCode(
            phone: String,
            verifyToken: String,
            cancelDeletion: Boolean
        ): Result<AuthLoginResult> {
            phoneCodeLoginPhone = phone
            phoneCodeLoginVerifyToken = verifyToken
            return Result.success(loginResult())
        }

        override suspend fun registerByAccount(
            account: String,
            password: String
        ): Result<AuthLoginResult> {
            registeredAccount = account
            registeredAccountPassword = password
            return Result.success(loginResult())
        }

        override suspend fun registerByEmailCode(
            email: String,
            emailCode: String
        ): Result<AuthLoginResult> {
            registeredEmail = email
            registeredEmailCode = emailCode
            return Result.success(loginResult())
        }

        override suspend fun registerByPhoneCode(
            phone: String,
            verifyToken: String
        ): Result<AuthLoginResult> {
            registeredPhone = phone
            registeredPhoneVerifyToken = verifyToken
            return Result.success(loginResult())
        }

        override suspend fun getFusionAuthToken(): Result<FusionAuthToken> = unused()

        override suspend fun changePassword(oldPassword: String, newPassword: String): Result<Unit> =
            unused()

        override suspend fun onboardingCompleted(): Result<Unit> = unused()

        override suspend fun bindSocial(
            platform: String,
            oauthCode: String,
            state: String?
        ): Result<User> = unused()

        override suspend fun logoutRemote(): Result<Unit> = unused()

        override suspend fun deleteAccountRemote(): Result<Unit> = unused()

        private fun <T> unused(): Result<T> =
            Result.failure(AssertionError("Unexpected repository call"))
    }

    private class FakeLocalAccountRepository : LocalAccountRepository {
        var savedUser: User? = null

        override fun isLoggedIn(): Boolean = savedUser != null

        override suspend fun getCurrentUser(): User? = savedUser

        override suspend fun getCurrentUserId(): Long? = savedUser?.userId

        override fun getUserFlow(): Flow<User?> = flowOf(savedUser)

        override suspend fun saveUser(user: User) {
            savedUser = user
        }

        override suspend fun clearUser() {
            savedUser = null
        }
    }

    private class FakeAccountSessionRepository : AccountSessionRepository {
        override suspend fun saveSession(session: AccountSession) = Unit

        override suspend fun clearSession() = Unit
    }

    private class FakeLoginBootstrapApplier : LoginBootstrapApplier {
        override suspend fun apply(bootstrap: LoginBootstrap?) = Unit
    }

    private class FakeSyncRepository : SyncRepository {
        override fun startPostLoginBootstrap() = Unit

        override suspend fun syncAfterLogin(): Result<Unit> = Result.success(Unit)

        override suspend fun restoreLearningPrerequisites(): Result<LearningPrerequisitesSnapshot> =
            Result.failure(UnsupportedOperationException())

        override fun getCurrentPostLoginBootstrapState(): PostLoginBootstrapState =
            PostLoginBootstrapState.Idle

        override fun scheduleBootstrapSync() = Unit

        override suspend fun discardLocalPendingSyncOnLogin() = Unit

        override fun observePostLoginBootstrapState(): Flow<PostLoginBootstrapState> =
            flowOf(PostLoginBootstrapState.Idle)

        override fun observePendingSyncCount(): Flow<Int> = flowOf(0)

        override fun observePendingSyncRecords(): Flow<List<SyncPendingRecord>> = flowOf(emptyList())

        override fun observeSyncBannerState(): Flow<SyncBannerState> = flowOf(SyncBannerState.Hidden)

        override fun triggerDrain() = Unit
    }

    private fun createLoginCompletionHandler(): LoginCompletionHandler =
        LoginCompletionHandler(
            localAccountRepository = FakeLocalAccountRepository(),
            accountSessionRepository = FakeAccountSessionRepository(),
            loginBootstrapApplier = FakeLoginBootstrapApplier(),
            syncFacade = SyncFacade(FakeSyncRepository())
        )

    private companion object {
        fun loginResult(): AuthLoginResult =
            AuthLoginResult(
                user = User(
                    userId = 7L,
                    email = null,
                    nickname = "tester",
                    gender = null,
                    avatarUrl = null,
                    phone = null,
                    qq = null,
                    wechat = null,
                    emailVerified = false,
                    onboardingCompleted = true
                ),
                session = AccountSession(
                    accessToken = "access",
                    refreshToken = "refresh",
                    expiresAtEpochMillis = 123_456L
                ),
                onboardingSnapshot = null,
                bootstrap = LoginBootstrap()
            )
    }
}
