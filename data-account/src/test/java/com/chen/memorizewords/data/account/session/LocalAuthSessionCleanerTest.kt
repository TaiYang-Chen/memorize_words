package com.chen.memorizewords.data.account.session

import com.chen.memorizewords.data.account.local.avatar.AvatarLocalDataSource
import com.chen.memorizewords.data.account.local.mmkv.auth.AuthLocalDataSource
import com.chen.memorizewords.domain.account.auth.SessionKickoutNotifier
import com.chen.memorizewords.domain.account.model.user.User
import com.chen.memorizewords.domain.account.repository.UserScopedDataCleaner
import com.chen.memorizewords.domain.sync.PostLoginBootstrapResetter
import javax.inject.Provider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking

class LocalAuthSessionCleanerTest {

    @Test
    fun `invalid session clears auth before user scoped data and then notifies kickout`() = runBlocking {
        val calls = mutableListOf<String>()
        val cleaner = LocalAuthSessionCleaner(
            sessionLocalDataSource = FakeSessionLocalDataSource(calls),
            authLocalDataSource = FakeAuthLocalDataSource(calls),
            sessionKickoutNotifier = FakeKickoutNotifier(calls),
            userScopedDataCleanerProvider = Provider {
                object : UserScopedDataCleaner {
                    override suspend fun clearUserScopedData() {
                        calls += "user_data"
                    }
                }
            },
            postLoginBootstrapResetter = object : PostLoginBootstrapResetter {
                override fun resetPostLoginBootstrap() {
                    calls += "bootstrap"
                }
            },
            avatarLocalDataSource = object : AvatarLocalDataSource {
                override fun saveAvatar(userId: Long, imageBytes: ByteArray): String = error("not used")
                override fun deleteAvatar(path: String?) {
                    calls += "avatar"
                }
            }
        )

        cleaner.clearLocalAuthState(notifyKickout = true)

        assertEquals(
            listOf("bootstrap", "session", "auth", "user_data", "avatar", "kickout"),
            calls
        )
    }
}

private class FakeSessionLocalDataSource(
    private val calls: MutableList<String>
) : SessionLocalDataSource {
    override fun saveSession(session: AuthSession) = Unit
    override fun getSession(): AuthSession? = null
    override fun observeSession(): Flow<AuthSession?> = flowOf(null)
    override fun clear() {
        calls += "session"
    }
}

private class FakeAuthLocalDataSource(
    private val calls: MutableList<String>
) : AuthLocalDataSource {
    override fun getUser(): User? = null
    override fun getUserFlow(): Flow<User?> = flowOf(null)
    override fun getUserId(): Long? = null
    override fun saveUser(user: User) = Unit
    override fun clearUser() = clear()
    override fun clear() {
        calls += "auth"
    }
    override fun onboardingCompleted() = Unit
}

private class FakeKickoutNotifier(
    private val calls: MutableList<String>
) : SessionKickoutNotifier {
    override val events = MutableSharedFlow<Unit>()
    override suspend fun notifyKickout() {
        calls += "kickout"
    }
}
