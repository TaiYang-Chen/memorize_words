package com.chen.memorizewords.startup

import com.chen.memorizewords.core.navigation.AppRoute
import com.chen.memorizewords.domain.account.orchestrator.startup.StartupLaunchDestination
import kotlin.test.Test
import kotlin.test.assertEquals

class StartupRouteResolverTest {

    private val resolver = StartupRouteResolver()

    @Test
    fun `home destination maps to home route`() {
        val route = resolver.resolveRoute(StartupLaunchDestination.HOME)

        assertEquals(AppRoute.Home, route)
    }

    @Test
    fun `onboarding destination maps to onboarding route`() {
        val route = resolver.resolveRoute(StartupLaunchDestination.ONBOARDING)

        assertEquals(AppRoute.Onboarding, route)
    }

    @Test
    fun `auth destination maps to auth route`() {
        val route = resolver.resolveRoute(StartupLaunchDestination.AUTH)

        assertEquals(AppRoute.Auth(), route)
    }
}
