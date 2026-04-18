package com.chen.memorizewords.domain.orchestrator.startup

import com.chen.memorizewords.domain.auth.AccessTokenState
import com.chen.memorizewords.domain.auth.AuthStateProvider
import com.chen.memorizewords.domain.auth.SessionKickoutNotifier
import com.chen.memorizewords.domain.auth.TokenProvider
import com.chen.memorizewords.domain.model.floating.FloatingDockState
import com.chen.memorizewords.domain.model.floating.FloatingWordSettings
import com.chen.memorizewords.domain.model.onboarding.OnboardingSnapshot
import com.chen.memorizewords.domain.model.onboarding.OnboardingStep
import com.chen.memorizewords.domain.repository.floating.FloatingWordSettingsRepository
import com.chen.memorizewords.domain.repository.onboarding.OnboardingRepository
import com.chen.memorizewords.domain.repository.user.AuthRepository
import com.chen.memorizewords.domain.service.onboarding.OnboardingStepResolver
import com.chen.memorizewords.domain.usecase.onboarding.GetCurrentOnboardingSnapshotUseCase
import com.chen.memorizewords.domain.usecase.onboarding.GetCurrentOnboardingStepUseCase
import java.util.concurrent.atomic.AtomicReference
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
            onboardingStep = OnboardingStep.SELECT_WORD_BOOK
        )

        val destination = orchestrator.resolveLaunchDestinationFast()

        assertEquals(StartupLaunchDestination.ONBOARDING, destination)
    }

    @Test
    fun `resolveLaunchDestinationFast returns home when onboarding completed`() {
        val orchestrator = buildOrchestrator(
            isAuthenticated = true,
            onboardingStep = OnboardingStep.COMPLETED
        )

        val destination = orchestrator.resolveLaunchDestinationFast()

        assertEquals(StartupLaunchDestination.HOME, destination)
    }

    @Test
    fun `resolveLaunchDestination returns auth when session invalid`() = runBlocking {
        val orchestrator = buildOrchestrator(
            isAuthenticated = true,
            onboardingStep = OnboardingStep.COMPLETED,
            accessTokenState = AccessTokenState.InvalidSession
        )

        val destination = orchestrator.resolveLaunchDestination(hasNetwork = true)

        assertEquals(StartupLaunchDestination.AUTH, destination)
    }

    @Test
    fun `resolveLaunchDestination keeps onboarding on slow token refresh`() = runBlocking {
        val orchestrator = buildOrchestrator(
            isAuthenticated = true,
            onboardingStep = OnboardingStep.SET_STUDY_PLAN,
            accessTokenState = AccessTokenState.TemporarilyUnavailable(RuntimeException("slow"))
        )

        val destination = orchestrator.resolveLaunchDestination(hasNetwork = true)

        assertEquals(StartupLaunchDestination.ONBOARDING, destination)
    }

    private fun buildOrchestrator(
        isAuthenticated: Boolean,
        onboardingStep: OnboardingStep,
        accessTokenState: AccessTokenState = AccessTokenState.Available("token")
    ): StartupOrchestrator {
        return StartupOrchestrator(
            authStateProvider = FakeAuthStateProvider(isAuthenticated),
            tokenProvider = FakeTokenProvider(accessTokenState),
            authRepository = FakeAuthRepository(isAuthenticated),
            floatingWordSettingsRepository = FakeFloatingWordSettingsRepository(),
            getCurrentOnboardingStepUseCase = GetCurrentOnboardingStepUseCase(
                getCurrentOnboardingSnapshotUseCase = GetCurrentOnboardingSnapshotUseCase(
                    FakeOnboardingRepository(onboardingStep)
                ),
                onboardingStepResolver = OnboardingStepResolver()
            ),
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

private class FakeFloatingWordSettingsRepository : FloatingWordSettingsRepository {
    private val settings = AtomicReference(FloatingWordSettings())

    override fun observeSettings(): Flow<FloatingWordSettings> = emptyFlow()

    override suspend fun getSettings(): FloatingWordSettings = settings.get()

    override suspend fun saveSettings(settings: FloatingWordSettings) {
        this.settings.set(settings)
    }

    override suspend fun updateBallPosition(x: Int, y: Int, dockState: FloatingDockState?) {
        settings.updateAndGet { current ->
            current.copy(
                floatingBallX = x,
                floatingBallY = y,
                dockState = dockState
            )
        }
    }
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

    override fun getUserFlow(): Flow<com.chen.memorizewords.domain.model.user.User?> = emptyFlow()
}

private class FakeOnboardingRepository(
    step: OnboardingStep
) : OnboardingRepository {
    private val snapshot = when (step) {
        OnboardingStep.SELECT_WORD_BOOK -> OnboardingSnapshot()
        OnboardingStep.SET_STUDY_PLAN -> OnboardingSnapshot(selectedWordBookId = 1L)
        OnboardingStep.COMPLETED -> OnboardingSnapshot(
            phase = com.chen.memorizewords.domain.model.onboarding.OnboardingPhase.COMPLETED,
            selectedWordBookId = 1L,
            completedAt = 100L
        )
    }

    override fun getCurrentSnapshot(): OnboardingSnapshot = snapshot

    override fun observeCurrentSnapshot(): Flow<OnboardingSnapshot> = emptyFlow()

    override suspend fun initializeSnapshotForUser(userId: Long, snapshot: OnboardingSnapshot?) =
        throw UnsupportedOperationException()

    override suspend fun replaceCurrentSnapshot(snapshot: OnboardingSnapshot?) =
        throw UnsupportedOperationException()

    override suspend fun completeOnboarding(selectedWordBookId: Long): OnboardingSnapshot =
        throw UnsupportedOperationException()
}
