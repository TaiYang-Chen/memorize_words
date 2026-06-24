package com.chen.memorizewords.startup

import com.chen.memorizewords.core.navigation.AppRoute
import com.chen.memorizewords.domain.account.orchestrator.startup.StartupLaunchDestination
import javax.inject.Inject

class StartupRouteResolver @Inject constructor() {

    fun resolveRoute(destination: StartupLaunchDestination): AppRoute {
        return when (destination) {
            StartupLaunchDestination.HOME -> AppRoute.Home
            StartupLaunchDestination.ONBOARDING -> AppRoute.Onboarding
            StartupLaunchDestination.AUTH -> AppRoute.Auth()
        }
    }
}
