package com.chen.memorizewords.domain.orchestrator.startup

import com.chen.memorizewords.domain.auth.AccessTokenState
import com.chen.memorizewords.domain.auth.AuthStateProvider
import com.chen.memorizewords.domain.auth.SessionKickoutNotifier
import com.chen.memorizewords.domain.auth.TokenProvider
import com.chen.memorizewords.domain.repository.floating.FloatingWordSettingsRepository
import com.chen.memorizewords.domain.repository.user.AuthRepository
import javax.inject.Inject

class StartupOrchestrator @Inject constructor(
    private val authStateProvider: AuthStateProvider,
    private val tokenProvider: TokenProvider,
    private val authRepository: AuthRepository,
    private val floatingWordSettingsRepository: FloatingWordSettingsRepository,
    val sessionKickoutNotifier: SessionKickoutNotifier
) {
    fun resolveLaunchDestinationFast(): StartupLaunchDestination {
        return if (authStateProvider.isAuthenticated()) {
            StartupLaunchDestination.HOME
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
            !hasNetwork -> StartupLaunchDestination.HOME
            accessTokenState is AccessTokenState.Available -> StartupLaunchDestination.HOME
            accessTokenState is AccessTokenState.TemporarilyUnavailable -> StartupLaunchDestination.HOME
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
}

enum class StartupLaunchDestination {
    HOME,
    AUTH
}
