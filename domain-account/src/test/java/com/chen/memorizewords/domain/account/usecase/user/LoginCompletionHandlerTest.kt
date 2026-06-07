package com.chen.memorizewords.domain.account.usecase.user

import com.chen.memorizewords.domain.account.model.AccountSession
import com.chen.memorizewords.domain.account.model.AuthLoginResult
import com.chen.memorizewords.domain.account.model.user.User
import com.chen.memorizewords.domain.account.repository.AccountSessionRepository
import com.chen.memorizewords.domain.account.repository.LocalAccountRepository
import com.chen.memorizewords.domain.sync.model.LearningPrerequisitesSnapshot
import com.chen.memorizewords.domain.sync.model.PostLoginBootstrapState
import com.chen.memorizewords.domain.sync.model.SyncBannerState
import com.chen.memorizewords.domain.sync.model.SyncPendingRecord
import com.chen.memorizewords.domain.sync.repository.SyncRepository
import com.chen.memorizewords.domain.sync.service.SyncFacade
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking

class LoginCompletionHandlerTest {

    @Test
    fun `complete saves login state and syncs after login`() = runBlocking {
        val localAccountRepository = FakeLocalAccountRepository()
        val accountSessionRepository = FakeAccountSessionRepository()
        val syncRepository = FakeSyncRepository()
        val handler = LoginCompletionHandler(
            localAccountRepository = localAccountRepository,
            accountSessionRepository = accountSessionRepository,
            syncFacade = SyncFacade(syncRepository)
        )
        val loginResult = createLoginResult()

        val user = handler.complete(loginResult)

        assertEquals(loginResult.user, user)
        assertEquals(loginResult.user, localAccountRepository.savedUser)
        assertEquals(loginResult.session, accountSessionRepository.savedSession)
        assertEquals(1, syncRepository.syncAfterLoginCalls)
    }

    @Test
    fun `complete throws sync error when post login sync fails`() = runBlocking {
        val syncRepository = FakeSyncRepository(syncResult = Result.failure(RuntimeException("offline")))
        val handler = LoginCompletionHandler(
            localAccountRepository = FakeLocalAccountRepository(),
            accountSessionRepository = FakeAccountSessionRepository(),
            syncFacade = SyncFacade(syncRepository)
        )

        assertFailsWith<LoginDataSyncError> {
            handler.complete(createLoginResult())
        }
        assertEquals(1, syncRepository.syncAfterLoginCalls)
    }
}

private fun createLoginResult(): AuthLoginResult {
    return AuthLoginResult(
        user = User(
            userId = 7L,
            email = null,
            nickname = "tester",
            gender = null,
            avatarUrl = null,
            phone = "123456",
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
        onboardingSnapshot = null
    )
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
    var savedSession: AccountSession? = null

    override suspend fun saveSession(session: AccountSession) {
        savedSession = session
    }

    override suspend fun clearSession() {
        savedSession = null
    }
}

private class FakeSyncRepository(
    private val syncResult: Result<Unit> = Result.success(Unit)
) : SyncRepository {
    var syncAfterLoginCalls: Int = 0

    override fun startPostLoginBootstrap() = Unit

    override suspend fun syncAfterLogin(): Result<Unit> {
        syncAfterLoginCalls++
        return syncResult
    }

    override suspend fun restoreLearningPrerequisites(): Result<LearningPrerequisitesSnapshot> {
        return Result.failure(UnsupportedOperationException())
    }

    override fun getCurrentPostLoginBootstrapState(): PostLoginBootstrapState {
        return PostLoginBootstrapState.Idle
    }

    override fun scheduleBootstrapSync() = Unit

    override fun observePostLoginBootstrapState(): Flow<PostLoginBootstrapState> {
        return flowOf(PostLoginBootstrapState.Idle)
    }

    override fun observePendingSyncCount(): Flow<Int> = flowOf(0)

    override fun observePendingSyncRecords(): Flow<List<SyncPendingRecord>> = flowOf(emptyList())

    override fun observeSyncBannerState(): Flow<SyncBannerState> = flowOf(SyncBannerState.Hidden)

    override fun triggerDrain() = Unit
}
