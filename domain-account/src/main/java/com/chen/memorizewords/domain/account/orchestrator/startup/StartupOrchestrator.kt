package com.chen.memorizewords.domain.account.orchestrator.startup
import com.chen.memorizewords.domain.account.auth.AccessTokenState
import com.chen.memorizewords.domain.account.auth.AuthStateProvider
import com.chen.memorizewords.domain.account.auth.SessionKickoutNotifier
import com.chen.memorizewords.domain.account.auth.TokenProvider
import javax.inject.Inject

class StartupOrchestrator @Inject constructor(
    private val authStateProvider: AuthStateProvider,
    private val tokenProvider: TokenProvider,
    private val onboardingStateReader: StartupOnboardingStateReader,
    private val floatingAutoStartReader: StartupFloatingAutoStartReader,
    val sessionKickoutNotifier: SessionKickoutNotifier
) {
    suspend fun resolveLaunchDestinationLocal(): StartupLaunchDestination {
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
        tokenProvider.resolveAccessTokenState(notifyKickoutOnInvalidSession = false)
    }

    suspend fun shouldAutoStartFloating(canDrawOverlays: Boolean): Boolean {
        if (!canDrawOverlays) return false
        if (!authStateProvider.isAuthenticated()) return false
        return floatingAutoStartReader.isAutoStartEnabled()
    }

    private suspend fun resolveAuthenticatedDestination(): StartupLaunchDestination {
        return if (onboardingStateReader.isOnboardingCompleted()) {
            StartupLaunchDestination.HOME
        } else {
            StartupLaunchDestination.ONBOARDING
        }
    }
}

interface StartupOnboardingStateReader {
    suspend fun isOnboardingCompleted(): Boolean
}

interface StartupFloatingAutoStartReader {
    suspend fun isAutoStartEnabled(): Boolean
}

enum class StartupLaunchDestination {
    HOME,
    ONBOARDING,
    AUTH
}
