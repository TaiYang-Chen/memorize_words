package com.chen.memorizewords.domain.account.usecase.user

import com.chen.memorizewords.domain.account.model.AccountSession
import com.chen.memorizewords.domain.account.model.AuthLoginResult
import com.chen.memorizewords.domain.account.model.user.User
import com.chen.memorizewords.domain.account.repository.AccountSessionRepository
import com.chen.memorizewords.domain.account.repository.LoginBootstrapApplier
import com.chen.memorizewords.domain.account.repository.LocalAccountRepository
import com.chen.memorizewords.domain.sync.model.LoginBootstrap
import com.chen.memorizewords.domain.sync.model.LearningPrerequisitesSnapshot
import com.chen.memorizewords.domain.sync.model.PostLoginBootstrapState
import com.chen.memorizewords.domain.sync.model.SyncBannerState
import com.chen.memorizewords.domain.sync.model.SyncPendingRecord
import com.chen.memorizewords.domain.sync.repository.SyncRepository
import com.chen.memorizewords.domain.sync.service.SyncFacade
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking

class LoginCompletionHandlerTest {

    @Test
    fun `complete saves login state applies bootstrap and starts background sync`() = runBlocking {
        val calls = mutableListOf<String>()
        val localAccountRepository = FakeLocalAccountRepository()
        val accountSessionRepository = FakeAccountSessionRepository()
        val loginBootstrapApplier = FakeLoginBootstrapApplier(calls = calls)
        val syncRepository = FakeSyncRepository(calls = calls)
        val handler = LoginCompletionHandler(
            localAccountRepository = localAccountRepository,
            accountSessionRepository = accountSessionRepository,
            loginBootstrapApplier = loginBootstrapApplier,
            syncFacade = SyncFacade(syncRepository)
        )
        val loginResult = createLoginResult()

        val user = handler.complete(loginResult)

        assertEquals(loginResult.user, user)
        assertEquals(loginResult.user, localAccountRepository.savedUser)
        assertEquals(loginResult.session, accountSessionRepository.savedSession)
        assertEquals(1, loginBootstrapApplier.applyCalls)
        assertEquals(1, syncRepository.startPostLoginBootstrapCalls)
        assertEquals(1, syncRepository.discardLocalPendingSyncOnLoginCalls)
        assertEquals(0, syncRepository.syncAfterLoginCalls)
        assertEquals(1, localAccountRepository.saveUserCalls)
        assertEquals(1, accountSessionRepository.saveSessionCalls)
        assertEquals(
            listOf("discardLocalPendingSyncOnLogin", "applyBootstrap", "startPostLoginBootstrap"),
            calls
        )
    }

    @Test
    fun `complete keeps local avatar for same user avatar`() = runBlocking {
        val localAccountRepository = FakeLocalAccountRepository()
        localAccountRepository.savedUser = createLoginResult().user.copy(localAvatarPath = "/local/avatar.jpg")
        val accountSessionRepository = FakeAccountSessionRepository()
        val syncRepository = FakeSyncRepository()
        val handler = LoginCompletionHandler(
            localAccountRepository = localAccountRepository,
            accountSessionRepository = accountSessionRepository,
            loginBootstrapApplier = FakeLoginBootstrapApplier(),
            syncFacade = SyncFacade(syncRepository)
        )
        val loginResult = createLoginResult()

        val user = handler.complete(loginResult)

        assertEquals("/local/avatar.jpg", user.localAvatarPath)
        assertEquals(user, localAccountRepository.savedUser)
        assertEquals(loginResult.session, accountSessionRepository.savedSession)
        assertEquals(1, syncRepository.startPostLoginBootstrapCalls)
        assertEquals(1, syncRepository.discardLocalPendingSyncOnLoginCalls)
        assertEquals(0, syncRepository.syncAfterLoginCalls)
        assertEquals(1, localAccountRepository.saveUserCalls)
        assertEquals(1, accountSessionRepository.saveSessionCalls)
    }

    @Test
    fun `complete still enters app when bootstrap apply fails`() = runBlocking {
        val syncRepository = FakeSyncRepository()
        val handler = LoginCompletionHandler(
            localAccountRepository = FakeLocalAccountRepository(),
            accountSessionRepository = FakeAccountSessionRepository(),
            loginBootstrapApplier = FakeLoginBootstrapApplier(failure = RuntimeException("local db failed")),
            syncFacade = SyncFacade(syncRepository)
        )

        val user = handler.complete(createLoginResult())

        assertEquals(7L, user.userId)
        assertEquals(1, syncRepository.startPostLoginBootstrapCalls)
        assertEquals(1, syncRepository.discardLocalPendingSyncOnLoginCalls)
        assertEquals(0, syncRepository.syncAfterLoginCalls)
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
        onboardingSnapshot = null,
        bootstrap = LoginBootstrap()
    )
}

private class FakeLocalAccountRepository : LocalAccountRepository {
    var savedUser: User? = null
    var saveUserCalls: Int = 0

    override fun isLoggedIn(): Boolean = savedUser != null

    override suspend fun getCurrentUser(): User? = savedUser

    override suspend fun getCurrentUserId(): Long? = savedUser?.userId

    override fun getUserFlow(): Flow<User?> = flowOf(savedUser)

    override suspend fun saveUser(user: User) {
        saveUserCalls++
        savedUser = user
    }

    override suspend fun clearUser() {
        savedUser = null
    }
}

private class FakeLoginBootstrapApplier(
    private val failure: Throwable? = null,
    private val calls: MutableList<String>? = null
) : LoginBootstrapApplier {
    var applyCalls: Int = 0
    var lastBootstrap: LoginBootstrap? = null

    override suspend fun apply(bootstrap: LoginBootstrap?) {
        applyCalls++
        lastBootstrap = bootstrap
        calls?.add("applyBootstrap")
        failure?.let { throw it }
    }
}

private class FakeAccountSessionRepository : AccountSessionRepository {
    var savedSession: AccountSession? = null
    var saveSessionCalls: Int = 0

    override suspend fun saveSession(session: AccountSession) {
        saveSessionCalls++
        savedSession = session
    }

    override suspend fun clearSession() {
        savedSession = null
    }
}

private class FakeSyncRepository(
    private val syncResults: List<Result<Unit>> = listOf(Result.success(Unit)),
    private val calls: MutableList<String>? = null
) : SyncRepository {
    var syncAfterLoginCalls: Int = 0
    var startPostLoginBootstrapCalls: Int = 0
    var discardLocalPendingSyncOnLoginCalls: Int = 0

    override fun startPostLoginBootstrap() {
        startPostLoginBootstrapCalls++
        calls?.add("startPostLoginBootstrap")
    }

    override suspend fun syncAfterLogin(): Result<Unit> {
        syncAfterLoginCalls++
        return syncResults.getOrElse(syncAfterLoginCalls - 1) { syncResults.last() }
    }

    override suspend fun restoreLearningPrerequisites(): Result<LearningPrerequisitesSnapshot> {
        return Result.failure(UnsupportedOperationException())
    }

    override fun getCurrentPostLoginBootstrapState(): PostLoginBootstrapState {
        return PostLoginBootstrapState.Idle
    }

    override fun scheduleBootstrapSync() = Unit

    override suspend fun discardLocalPendingSyncOnLogin() {
        discardLocalPendingSyncOnLoginCalls++
        calls?.add("discardLocalPendingSyncOnLogin")
    }

    override fun observePostLoginBootstrapState(): Flow<PostLoginBootstrapState> {
        return flowOf(PostLoginBootstrapState.Idle)
    }

    override fun observePendingSyncCount(): Flow<Int> = flowOf(0)

    override fun observePendingSyncRecords(): Flow<List<SyncPendingRecord>> = flowOf(emptyList())

    override fun observeSyncBannerState(): Flow<SyncBannerState> = flowOf(SyncBannerState.Hidden)

    override fun triggerDrain() = Unit
}
