package com.chen.memorizewords.domain.orchestrator.startup

import com.chen.memorizewords.domain.auth.AccessTokenState
import com.chen.memorizewords.domain.auth.AuthStateProvider
import com.chen.memorizewords.domain.auth.SessionKickoutNotifier
import com.chen.memorizewords.domain.auth.TokenProvider
import com.chen.memorizewords.domain.repository.floating.FloatingWordSettingsRepository
import com.chen.memorizewords.domain.repository.user.AuthRepository
import com.chen.memorizewords.domain.usecase.onboarding.GetCurrentOnboardingStepUseCase
import com.chen.memorizewords.domain.model.onboarding.OnboardingStep
import javax.inject.Inject

class StartupOrchestrator @Inject constructor(
    private val authStateProvider: AuthStateProvider,
    private val tokenProvider: TokenProvider,
    private val authRepository: AuthRepository,
    private val floatingWordSettingsRepository: FloatingWordSettingsRepository,
    private val getCurrentOnboardingStepUseCase: GetCurrentOnboardingStepUseCase,
    val sessionKickoutNotifier: SessionKickoutNotifier
) {
    fun resolveLaunchDestinationFast(): StartupLaunchDestination {
        return if (authStateProvider.isAuthenticated()) {
            resolveAuthenticatedDestination()
        } else {
            StartupLaunchDestination.AUTH
        }
    }

    suspend fun resolveLaunchDestination(hasNetwork: Boolean): StartupLaunchDestination {
        val isAuthenticated = authStateProvider.isAuthenticated()
        val accessTokenState = if (isAuthenticated && hasNetwork) {
            tokenProvider.resolveAccessTokenState()
        } else {
            AccessTokenState.NoSession
        }

        return when {
            !isAuthenticated -> StartupLaunchDestination.AUTH
            !hasNetwork -> resolveAuthenticatedDestination()
            accessTokenState is AccessTokenState.Available -> resolveAuthenticatedDestination()
            accessTokenState is AccessTokenState.TemporarilyUnavailable -> resolveAuthenticatedDestination()
            accessTokenState == AccessTokenState.NoSession -> StartupLaunchDestination.AUTH
            accessTokenState == AccessTokenState.InvalidSession -> StartupLaunchDestination.AUTH
            else -> StartupLaunchDestination.AUTH
        }
    }

    suspend fun warmUpSessionStateIfNeeded(hasNetwork: Boolean) {
        if (!hasNetwork || !authStateProvider.isAuthenticated()) return
        tokenProvider.resolveAccessTokenState()
    }

    suspend fun shouldAutoStartFloating(canDrawOverlays: Boolean): Boolean {
        if (!canDrawOverlays) return false
        if (!authRepository.isLoggedIn()) return false
        val settings = floatingWordSettingsRepository.getSettings()
        return settings.enabled && settings.autoStartOnAppLaunch
    }

    private fun resolveAuthenticatedDestination(): StartupLaunchDestination {
        return when (getCurrentOnboardingStepUseCase()) {
            OnboardingStep.COMPLETED -> StartupLaunchDestination.HOME
            OnboardingStep.SELECT_WORD_BOOK,
            OnboardingStep.SET_STUDY_PLAN -> StartupLaunchDestination.ONBOARDING
        }
    }
}

enum class StartupLaunchDestination {
    HOME,
    ONBOARDING,
    AUTH
}
