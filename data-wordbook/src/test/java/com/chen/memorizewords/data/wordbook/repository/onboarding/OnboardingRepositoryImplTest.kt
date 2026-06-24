package com.chen.memorizewords.data.wordbook.repository.onboarding

import com.chen.memorizewords.data.wordbook.local.mmkv.onboarding.OnboardingSnapshotDataSource
import com.chen.memorizewords.domain.account.auth.LocalAccountStore
import com.chen.memorizewords.domain.account.model.user.User
import com.chen.memorizewords.domain.account.repository.LocalAccountRepository
import com.chen.memorizewords.domain.sync.OutboxCommand
import com.chen.memorizewords.domain.sync.SyncOutboxWriter
import com.chen.memorizewords.domain.wordbook.model.onboarding.OnboardingPhase
import com.chen.memorizewords.domain.wordbook.model.onboarding.OnboardingSnapshot
import com.google.gson.Gson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking

class OnboardingRepositoryImplTest {

    @Test
    fun `complete onboarding updates local user cache`() = runBlocking {
        val accountRepository = FakeLocalAccountRepository(createUser(onboardingCompleted = false))
        val snapshotDataSource = FakeOnboardingSnapshotDataSource()
        val repository = OnboardingRepositoryImpl(
            localAccountStore = accountRepository,
            localAccountRepository = accountRepository,
            onboardingSnapshotDataSource = snapshotDataSource,
            syncOutboxWriter = FakeSyncOutboxWriter(),
            gson = Gson()
        )

        val snapshot = repository.completeOnboarding(selectedWordBookId = 1001L)

        assertEquals(OnboardingPhase.COMPLETED, snapshot.phase)
        assertTrue(accountRepository.currentUser!!.onboardingCompleted)
    }

    private fun createUser(onboardingCompleted: Boolean): User {
        return User(
            userId = 7L,
            email = "demo@example.com",
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
}

private class FakeLocalAccountRepository(
    initialUser: User?
) : LocalAccountRepository, LocalAccountStore {
    private val userFlow = MutableStateFlow(initialUser)
    var currentUser: User? = initialUser
        private set

    override fun isLoggedIn(): Boolean = currentUser != null

    override suspend fun getCurrentUser(): User? = currentUser

    override suspend fun getCurrentUserId(): Long? = currentUser?.userId

    override fun getUserFlow(): Flow<User?> = userFlow

    override fun getUserId(): Long? = currentUser?.userId

    override suspend fun saveUser(user: User) {
        currentUser = user
        userFlow.value = user
    }

    override suspend fun clearUser() {
        currentUser = null
        userFlow.value = null
    }
}

private class FakeOnboardingSnapshotDataSource : OnboardingSnapshotDataSource {
    private val snapshots = mutableMapOf<Long, MutableStateFlow<OnboardingSnapshot?>>()

    override fun getSnapshot(userId: Long): OnboardingSnapshot? = snapshots[userId]?.value

    override fun observeSnapshot(userId: Long): Flow<OnboardingSnapshot?> {
        return snapshots.getOrPut(userId) { MutableStateFlow(null) }
    }

    override suspend fun saveSnapshot(userId: Long, snapshot: OnboardingSnapshot) {
        snapshots.getOrPut(userId) { MutableStateFlow(null) }.value = snapshot
    }

    override suspend fun clearSnapshot(userId: Long) {
        snapshots.getOrPut(userId) { MutableStateFlow(null) }.value = null
    }
}

private class FakeSyncOutboxWriter : SyncOutboxWriter {
    override suspend fun enqueueLatest(command: OutboxCommand) = Unit
}
