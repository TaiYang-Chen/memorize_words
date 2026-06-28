package com.chen.memorizewords.domain.account.orchestrator.startup

import com.chen.memorizewords.domain.account.auth.AccessTokenState
import com.chen.memorizewords.domain.account.auth.AuthStateProvider
import com.chen.memorizewords.domain.account.auth.SessionKickoutNotifier
import com.chen.memorizewords.domain.account.auth.TokenProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class StartupOrchestratorTest {
    @Test
    fun `local launch returns auth when user is not authenticated`() = runBlocking {
        val orchestrator = createOrchestrator(isAuthenticated = false)

        val destination = orchestrator.resolveLaunchDestinationLocal()

        assertEquals(StartupLaunchDestination.AUTH, destination)
    }

    @Test
    fun `local launch returns onboarding for authenticated user when onboarding is incomplete`() = runBlocking {
        val orchestrator = createOrchestrator(
            isAuthenticated = true,
            onboardingCompleted = false
        )

        val destination = orchestrator.resolveLaunchDestinationLocal()

        assertEquals(StartupLaunchDestination.ONBOARDING, destination)
    }

    @Test
    fun `local launch returns home for authenticated user when onboarding is complete`() = runBlocking {
        val orchestrator = createOrchestrator(
            isAuthenticated = true,
            onboardingCompleted = true
        )

        val destination = orchestrator.resolveLaunchDestinationLocal()

        assertEquals(StartupLaunchDestination.HOME, destination)
    }

    @Test
    fun `network launch returns onboarding for authenticated user with available token when onboarding is incomplete`() = runBlocking {
        val orchestrator = createOrchestrator(
            isAuthenticated = true,
            onboardingCompleted = false,
            accessTokenState = AccessTokenState.Available("token")
        )

        val destination = orchestrator.resolveLaunchDestination(hasNetwork = true)

        assertEquals(StartupLaunchDestination.ONBOARDING, destination)
    }

    @Test
    fun `network launch returns onboarding for authenticated user with temporarily unavailable token when onboarding is incomplete`() = runBlocking {
        val orchestrator = createOrchestrator(
            isAuthenticated = true,
            onboardingCompleted = false,
            accessTokenState = AccessTokenState.TemporarilyUnavailable(RuntimeException("offline"))
        )

        val destination = orchestrator.resolveLaunchDestination(hasNetwork = true)

        assertEquals(StartupLaunchDestination.ONBOARDING, destination)
    }

    @Test
    fun `network launch returns auth for authenticated user with invalid session`() = runBlocking {
        val orchestrator = createOrchestrator(
            isAuthenticated = true,
            onboardingCompleted = true,
            accessTokenState = AccessTokenState.InvalidSession
        )

        val destination = orchestrator.resolveLaunchDestination(hasNetwork = true)

        assertEquals(StartupLaunchDestination.AUTH, destination)
    }

    @Test
    fun `network launch returns local destination when authenticated user is offline`() = runBlocking {
        val tokenProvider = FakeTokenProvider(AccessTokenState.InvalidSession)
        val orchestrator = createOrchestrator(
            isAuthenticated = true,
            onboardingCompleted = true,
            tokenProvider = tokenProvider
        )

        val destination = orchestrator.resolveLaunchDestination(hasNetwork = false)

        assertEquals(StartupLaunchDestination.HOME, destination)
        assertEquals(null, tokenProvider.lastNotifyKickoutOnInvalidSession)
    }

    @Test
    fun `session warmup notifies kickout when cached session is invalid`() = runBlocking {
        val tokenProvider = FakeTokenProvider(AccessTokenState.InvalidSession)
        val orchestrator = createOrchestrator(
            isAuthenticated = true,
            tokenProvider = tokenProvider
        )

        orchestrator.warmUpSessionStateIfNeeded(hasNetwork = true)

        assertEquals(true, tokenProvider.lastNotifyKickoutOnInvalidSession)
    }

    private fun createOrchestrator(
        isAuthenticated: Boolean,
        onboardingCompleted: Boolean = false,
        accessTokenState: AccessTokenState = AccessTokenState.NoSession,
        tokenProvider: FakeTokenProvider = FakeTokenProvider(accessTokenState)
    ): StartupOrchestrator {
        return StartupOrchestrator(
            authStateProvider = FakeAuthStateProvider(isAuthenticated),
            tokenProvider = tokenProvider,
            onboardingStateReader = FakeStartupOnboardingStateReader(onboardingCompleted),
            floatingAutoStartReader = FakeStartupFloatingAutoStartReader(),
            sessionKickoutNotifier = FakeSessionKickoutNotifier()
        )
    }
}

private class FakeAuthStateProvider(
    private val isAuthenticated: Boolean
) : AuthStateProvider {
    override fun isAuthenticated(): Boolean = isAuthenticated

    override fun observeAuthenticated(): Flow<Boolean> = flowOf(isAuthenticated)
}

private class FakeTokenProvider(
    private val accessTokenState: AccessTokenState
) : TokenProvider {
    var lastNotifyKickoutOnInvalidSession: Boolean? = null

    override suspend fun resolveAccessTokenState(
        notifyKickoutOnInvalidSession: Boolean
    ): AccessTokenState {
        lastNotifyKickoutOnInvalidSession = notifyKickoutOnInvalidSession
        return accessTokenState
    }

    override fun getAccessTokenIfValid(): String? {
        return (accessTokenState as? AccessTokenState.Available)?.token
    }
}

private class FakeStartupOnboardingStateReader(
    private val completed: Boolean
) : StartupOnboardingStateReader {
    override suspend fun isOnboardingCompleted(): Boolean = completed
}

private class FakeStartupFloatingAutoStartReader : StartupFloatingAutoStartReader {
    override suspend fun isAutoStartEnabled(): Boolean = false
}

private class FakeSessionKickoutNotifier : SessionKickoutNotifier {
    override val events: Flow<Unit> = MutableSharedFlow()

    override suspend fun notifyKickout() = Unit
}
