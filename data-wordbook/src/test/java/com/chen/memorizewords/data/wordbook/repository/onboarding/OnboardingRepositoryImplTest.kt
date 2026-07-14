package com.chen.memorizewords.data.wordbook.repository.onboarding

import com.chen.memorizewords.data.wordbook.local.mmkv.onboarding.OnboardingSnapshotDataSource
import com.chen.memorizewords.core.common.coroutines.DirectSyncLauncher
import com.chen.memorizewords.data.wordbook.remote.datasync.RemoteUserSyncDataSource
import com.chen.memorizewords.domain.account.auth.LocalAccountStore
import com.chen.memorizewords.domain.account.model.user.User
import com.chen.memorizewords.domain.account.repository.LocalAccountRepository
import com.chen.memorizewords.domain.wordbook.model.onboarding.OnboardingPhase
import com.chen.memorizewords.domain.wordbook.model.onboarding.OnboardingSnapshot
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

class OnboardingRepositoryImplTest {

    @Test
    fun `complete onboarding updates local user cache`() = runBlocking {
        val accountRepository = FakeLocalAccountRepository(createUser(onboardingCompleted = false))
        val snapshotDataSource = FakeOnboardingSnapshotDataSource()
        val repository = OnboardingRepositoryImpl(
            localAccountStore = accountRepository,
            localAccountRepository = accountRepository,
            onboardingSnapshotDataSource = snapshotDataSource,
            remoteUserSyncDataSource = proxy { methodName ->
                if (methodName.startsWith("updateOnboardingState")) Result.success(Unit)
                else error("Unexpected call: $methodName")
            },
            directSyncLauncher = DirectSyncLauncher(CoroutineScope(Dispatchers.Unconfined))
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

    @Suppress("UNCHECKED_CAST")
    private inline fun <reified T : Any> proxy(
        crossinline handler: (String) -> Any?
    ): T = Proxy.newProxyInstance(
        T::class.java.classLoader,
        arrayOf(T::class.java),
        InvocationHandler { _, method, _ -> handler(method.name) }
    ) as T
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
