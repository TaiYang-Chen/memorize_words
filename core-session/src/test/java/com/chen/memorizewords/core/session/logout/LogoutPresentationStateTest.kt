package com.chen.memorizewords.core.session.logout

import com.chen.memorizewords.domain.account.model.LogoutOutcome
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class LogoutPresentationStateTest {

    @Test
    fun holdsIdentityUntilNavigationHostStops() = runTest {
        val source = MutableStateFlow<String?>("Alice")
        val logoutState = MutableStateFlow<LogoutState>(LogoutState.Idle)
        val presentationScope = CoroutineScope(StandardTestDispatcher(testScheduler))
        val presentation = LogoutPresentationState(
            source = source,
            logoutState = logoutState,
            scope = presentationScope,
            initialValue = source.value
        )
        advanceUntilIdle()

        logoutState.value = LogoutState.AwaitingLoadingFrame(1L, force = false)
        source.value = null
        advanceUntilIdle()
        assertEquals("Alice", presentation.value.value)

        logoutState.value = LogoutState.ReadyToNavigate(
            requestId = 1L,
            terminal = LogoutTerminal.Success(LogoutOutcome.Success)
        )
        advanceUntilIdle()
        assertEquals("Alice", presentation.value.value)

        logoutState.value = LogoutState.Navigating(1L, LogoutHostId(1L))
        advanceUntilIdle()
        assertEquals("Alice", presentation.value.value)

        logoutState.value = LogoutState.Idle
        advanceUntilIdle()
        assertEquals(null, presentation.value.value)
        presentationScope.cancel()
    }

    @Test
    fun releasesIdentityForRiskConfirmationAndFailure() = runTest {
        val source = MutableStateFlow<String?>("Alice")
        val logoutState = MutableStateFlow<LogoutState>(LogoutState.Idle)
        val presentationScope = CoroutineScope(StandardTestDispatcher(testScheduler))
        val presentation = LogoutPresentationState(
            source = source,
            logoutState = logoutState,
            scope = presentationScope,
            initialValue = source.value
        )
        advanceUntilIdle()

        logoutState.value = LogoutState.AwaitingLoadingFrame(1L, false)
        source.value = null
        advanceUntilIdle()
        assertEquals("Alice", presentation.value.value)

        logoutState.value = LogoutState.AwaitingRiskConfirmation(1L)
        advanceUntilIdle()
        assertEquals(null, presentation.value.value)

        source.value = "Bob"
        logoutState.value = LogoutState.Failed(2L, IllegalStateException("failed"))
        advanceUntilIdle()
        assertEquals("Bob", presentation.value.value)
        presentationScope.cancel()
    }
}
