package com.chen.memorizewords.core.ui.session.logout

import com.chen.memorizewords.core.session.logout.LogoutState
import com.chen.memorizewords.core.session.logout.LogoutTerminal
import com.chen.memorizewords.core.session.logout.SessionLogoutCoordinator
import com.chen.memorizewords.core.session.logout.SessionLogoutExecutor
import com.chen.memorizewords.domain.account.model.LogoutOutcome
import com.chen.memorizewords.domain.account.repository.user.LogoutDataLossRiskException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class SessionLogoutHostControllerTest {

    @Test
    fun idleDismissesRestoredDialogs() = runTest {
        val callbacks = FakeCallbacks()
        val controller = createController(
            coordinator = coordinator(FakeExecutor(Result.success(LogoutOutcome.Success))),
            callbacks = callbacks
        )

        controller.render(LogoutState.Idle)

        assertEquals(1, callbacks.hideLoadingCount)
        assertEquals(1, callbacks.dismissRiskCount)
    }

    @Test
    fun loadingFrameCallbackIsTheOnlyExecutionGate() = runTest {
        val executor = FakeExecutor(Result.success(LogoutOutcome.Success))
        val coordinator = coordinator(executor)
        val callbacks = FakeCallbacks()
        val controller = createController(coordinator, callbacks)
        coordinator.requestLogout()

        controller.render(coordinator.state.value)
        assertEquals(0, executor.callCount)

        callbacks.loadingPresented?.invoke()
        advanceUntilIdle()
        assertEquals(1, executor.callCount)
        assertIs<LogoutState.ReadyToNavigate>(coordinator.state.value)
    }

    @Test
    fun navigationKeepsWorkflowLockedUntilHostStops() = runTest {
        val coordinator = coordinator(FakeExecutor(Result.success(LogoutOutcome.Success)))
        val callbacks = FakeCallbacks()
        val controller = createController(coordinator, callbacks)
        coordinator.requestLogout()
        val awaiting = assertIs<LogoutState.AwaitingLoadingFrame>(coordinator.state.value)
        coordinator.presentLoadingFrame(awaiting.requestId)
        advanceUntilIdle()

        controller.render(coordinator.state.value)

        assertEquals(1, callbacks.navigateCount)
        assertEquals(1, callbacks.completedCount)
        assertIs<LogoutState.Navigating>(coordinator.state.value)

        controller.onStop()
        assertEquals(LogoutState.Idle, coordinator.state.value)
    }

    @Test
    fun navigationExceptionDoesNotEscapeAndAllowsAnotherHostToTakeOver() = runTest {
        val coordinator = coordinator(FakeExecutor(Result.success(LogoutOutcome.Success)))
        val failingCallbacks = FakeCallbacks(navigationFailure = IllegalStateException("route failed"))
        val failingController = createController(coordinator, failingCallbacks)
        coordinator.requestLogout()
        val awaiting = assertIs<LogoutState.AwaitingLoadingFrame>(coordinator.state.value)
        coordinator.presentLoadingFrame(awaiting.requestId)
        advanceUntilIdle()

        failingController.render(coordinator.state.value)

        val ready = assertIs<LogoutState.ReadyToNavigate>(coordinator.state.value)
        assertNull(ready.claimedBy)
        failingController.render(ready)
        assertEquals(1, failingCallbacks.navigateCount)

        failingController.onResume()
        failingController.render(coordinator.state.value)
        assertEquals(2, failingCallbacks.navigateCount)

        val succeedingCallbacks = FakeCallbacks()
        val succeedingController = createController(coordinator, succeedingCallbacks)
        succeedingController.render(coordinator.state.value)

        assertEquals(1, succeedingCallbacks.navigateCount)
        assertEquals(1, succeedingCallbacks.completedCount)
        assertIs<LogoutState.Navigating>(coordinator.state.value)
    }

    @Test
    fun destroyingHostReleasesRiskDialogLease() = runTest {
        val coordinator = coordinator(FakeExecutor(Result.failure(LogoutDataLossRiskException())))
        val callbacks = FakeCallbacks()
        val controller = createController(coordinator, callbacks)
        coordinator.requestLogout()
        val awaiting = assertIs<LogoutState.AwaitingLoadingFrame>(coordinator.state.value)
        coordinator.presentLoadingFrame(awaiting.requestId)
        advanceUntilIdle()

        controller.render(coordinator.state.value)
        val claimed = assertIs<LogoutState.AwaitingRiskConfirmation>(coordinator.state.value)
        assertEquals(1, callbacks.showRiskCount)
        controller.onDestroy()

        val released = assertIs<LogoutState.AwaitingRiskConfirmation>(coordinator.state.value)
        assertEquals(claimed.requestId, released.requestId)
        assertNull(released.claimedBy)
    }

    @Test
    fun repeatedRiskStateRendersOnlyOneDialog() = runTest {
        val coordinator = coordinator(FakeExecutor(Result.failure(LogoutDataLossRiskException())))
        val callbacks = FakeCallbacks()
        val controller = createController(coordinator, callbacks)
        coordinator.requestLogout()
        val awaiting = assertIs<LogoutState.AwaitingLoadingFrame>(coordinator.state.value)
        coordinator.presentLoadingFrame(awaiting.requestId)
        advanceUntilIdle()

        controller.render(coordinator.state.value)
        controller.render(coordinator.state.value)

        assertEquals(1, callbacks.showRiskCount)
    }

    @Test
    fun onlyOneResumedHostOwnsAndPresentsTheLoadingDialog() = runTest {
        val coordinator = coordinator(FakeExecutor(Result.success(LogoutOutcome.Success)))
        val firstCallbacks = FakeCallbacks()
        val secondCallbacks = FakeCallbacks()
        val firstController = createController(coordinator, firstCallbacks)
        val secondController = createController(coordinator, secondCallbacks)
        coordinator.requestLogout()

        firstController.render(coordinator.state.value)
        secondController.render(coordinator.state.value)

        assertEquals(1, firstCallbacks.showLoadingCount)
        assertEquals(0, secondCallbacks.showLoadingCount)
        assertEquals(1, secondCallbacks.hideLoadingCount)

        firstCallbacks.loadingPresented?.invoke()
        advanceUntilIdle()
        assertIs<LogoutState.ReadyToNavigate>(coordinator.state.value)
    }

    private fun SessionLogoutCoordinator.presentLoadingFrame(requestId: Long) {
        val hostId = createHostId()
        assertTrue(claimLoadingHost(requestId, hostId))
        onLoadingFramePresented(requestId, hostId)
    }

    private fun TestScope.coordinator(executor: SessionLogoutExecutor): SessionLogoutCoordinator {
        val dispatcher = StandardTestDispatcher(testScheduler)
        return SessionLogoutCoordinator(executor, this, dispatcher)
    }

    private fun createController(
        coordinator: SessionLogoutCoordinator,
        callbacks: FakeCallbacks
    ): SessionLogoutHostController {
        return SessionLogoutHostController(
            coordinator = coordinator,
            callbacks = callbacks,
            loadingMessage = "loading",
            riskTitle = "risk",
            riskMessage = "message"
        )
    }

    private class FakeExecutor(private val result: Result<LogoutOutcome>) : SessionLogoutExecutor {
        var callCount = 0

        override suspend fun execute(force: Boolean): Result<LogoutOutcome> {
            callCount += 1
            return result
        }
    }

    private class FakeCallbacks(
        private val navigationFailure: Throwable? = null
    ) : SessionLogoutHostController.Callbacks {
        var loadingPresented: (() -> Unit)? = null
        var showLoadingCount = 0
        var hideLoadingCount = 0
        var dismissRiskCount = 0
        var showRiskCount = 0
        var completedCount = 0
        var navigateCount = 0

        override fun showLoading(message: String, onPresented: (() -> Unit)?): Boolean {
            showLoadingCount += 1
            loadingPresented = onPresented
            return true
        }

        override fun hideLoading() {
            hideLoadingCount += 1
        }

        override fun showRiskDialog(requestId: Long, title: String, message: String): Boolean {
            showRiskCount += 1
            return true
        }

        override fun dismissRiskDialog() {
            dismissRiskCount += 1
        }

        override fun onCompleted(terminal: LogoutTerminal) {
            completedCount += 1
        }

        override fun onFailed(failure: Throwable) = Unit

        override fun navigateToAuth() {
            navigateCount += 1
            navigationFailure?.let { throw it }
        }
    }
}
