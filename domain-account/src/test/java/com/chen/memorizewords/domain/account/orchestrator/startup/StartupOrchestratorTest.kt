package com.chen.memorizewords.domain.account.orchestrator.startup
import com.chen.memorizewords.domain.account.auth.AccessTokenState
import com.chen.memorizewords.domain.account.auth.AuthStateProvider
import com.chen.memorizewords.domain.account.auth.SessionKickoutNotifier
import com.chen.memorizewords.domain.account.auth.TokenProvider
import com.chen.memorizewords.domain.account.repository.user.AuthRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class StartupOrchestratorTest {

    @Test
    fun `resolveLaunchDestinationFast returns onboarding when authenticated but incomplete`() {
        val orchestrator = buildOrchestrator(
            isAuthenticated = true,
            onboardingCompleted = false
        )

        val destination = orchestrator.resolveLaunchDestinationFast()

        assertEquals(StartupLaunchDestination.ONBOARDING, destination)
    }

    @Test
    fun `resolveLaunchDestinationFast returns home when onboarding completed`() {
        val orchestrator = buildOrchestrator(
            isAuthenticated = true,
            onboardingCompleted = true
        )

        val destination = orchestrator.resolveLaunchDestinationFast()

        assertEquals(StartupLaunchDestination.HOME, destination)
    }

    @Test
    fun `resolveLaunchDestination returns auth when session invalid`() = runBlocking {
        val orchestrator = buildOrchestrator(
            isAuthenticated = true,
            onboardingCompleted = true,
            accessTokenState = AccessTokenState.InvalidSession
        )

        val destination = orchestrator.resolveLaunchDestination(hasNetwork = true)

        assertEquals(StartupLaunchDestination.AUTH, destination)
    }

    @Test
    fun `resolveLaunchDestination keeps onboarding on slow token refresh`() = runBlocking {
        val orchestrator = buildOrchestrator(
            isAuthenticated = true,
            onboardingCompleted = false,
            accessTokenState = AccessTokenState.TemporarilyUnavailable(RuntimeException("slow"))
        )

        val destination = orchestrator.resolveLaunchDestination(hasNetwork = true)

        assertEquals(StartupLaunchDestination.ONBOARDING, destination)
    }

    private fun buildOrchestrator(
        isAuthenticated: Boolean,
        onboardingCompleted: Boolean,
        accessTokenState: AccessTokenState = AccessTokenState.Available("token")
    ): StartupOrchestrator {
        return StartupOrchestrator(
            authStateProvider = FakeAuthStateProvider(isAuthenticated),
            tokenProvider = FakeTokenProvider(accessTokenState),
            authRepository = FakeAuthRepository(isAuthenticated),
            onboardingStateReader = FakeStartupOnboardingStateReader(onboardingCompleted),
            floatingAutoStartReader = FakeStartupFloatingAutoStartReader(),
            sessionKickoutNotifier = FakeSessionKickoutNotifier()
        )
    }
}

private class FakeAuthStateProvider(
    private val authenticated: Boolean
) : AuthStateProvider {
    override fun isAuthenticated(): Boolean = authenticated

    override fun observeAuthenticated(): Flow<Boolean> = emptyFlow()
}

private class FakeTokenProvider(
    private val state: AccessTokenState
) : TokenProvider {
    override suspend fun resolveAccessTokenState(): AccessTokenState = state

    override fun getAccessTokenIfValid(): String? {
        return (state as? AccessTokenState.Available)?.token
    }
}

private class FakeStartupOnboardingStateReader(
    private val completed: Boolean
) : StartupOnboardingStateReader {
    override fun isOnboardingCompleted(): Boolean = completed
}

private class FakeStartupFloatingAutoStartReader : StartupFloatingAutoStartReader {
    override suspend fun isAutoStartEnabled(): Boolean = false
}

private class FakeSessionKickoutNotifier : SessionKickoutNotifier {
    override val events: Flow<Unit> = MutableSharedFlow()

    override suspend fun notifyKickout() = Unit
}

private class FakeAuthRepository(
    private val loggedIn: Boolean
) : AuthRepository {
    override suspend fun login(phoneNumber: String, password: String) =
        throw UnsupportedOperationException()

    override suspend fun sendLoginSmsCode(phone: String) =
        throw UnsupportedOperationException()

    override suspend fun loginBySms(phone: String, code: String) =
        throw UnsupportedOperationException()

    override suspend fun loginByWechat(oauthCode: String, state: String?) =
        throw UnsupportedOperationException()

    override suspend fun loginByQq(oauthCode: String, state: String?) =
        throw UnsupportedOperationException()

    override suspend fun register(phoneNumber: String, password: String) =
        throw UnsupportedOperationException()

    override suspend fun changePassword(oldPassword: String, newPassword: String) =
        throw UnsupportedOperationException()

    override suspend fun bindSocial(platform: String, oauthCode: String, state: String?) =
        throw UnsupportedOperationException()

    override suspend fun logout(force: Boolean) = throw UnsupportedOperationException()

    override suspend fun deleteAccount() = throw UnsupportedOperationException()

    override fun isLoggedIn(): Boolean = loggedIn

    override suspend fun getCurrentUser() = null

    override fun getUserFlow(): Flow<com.chen.memorizewords.domain.account.model.user.User?> = emptyFlow()
}
