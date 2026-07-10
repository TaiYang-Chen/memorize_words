package com.chen.memorizewords.core.session.logout

import com.chen.memorizewords.domain.account.model.LogoutOutcome
import com.chen.memorizewords.domain.account.repository.user.LogoutDataLossRiskException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class SessionLogoutCoordinatorTest {

    @Test
    fun doesNotExecuteBeforeMatchingLoadingFrame() = runTest {
        val executor = FakeExecutor(listOf(Result.success(LogoutOutcome.Success)))
        val dispatcher = StandardTestDispatcher(testScheduler)
        val coordinator = SessionLogoutCoordinator(executor, this, dispatcher)

        coordinator.requestLogout()
        val awaiting = assertIs<LogoutState.AwaitingLoadingFrame>(coordinator.state.value)
        val loadingHost = coordinator.createHostId()
        assertTrue(coordinator.claimLoadingHost(awaiting.requestId, loadingHost))
        assertEquals(emptyList(), executor.forces)

        coordinator.onLoadingFramePresented(awaiting.requestId + 1L, loadingHost)
        advanceUntilIdle()
        assertEquals(emptyList(), executor.forces)

        coordinator.onLoadingFramePresented(awaiting.requestId, loadingHost)
        advanceUntilIdle()
        assertEquals(listOf(false), executor.forces)
        assertIs<LogoutState.ReadyToNavigate>(coordinator.state.value)

        coordinator.onLoadingFramePresented(awaiting.requestId, loadingHost)
        advanceUntilIdle()
        assertEquals(listOf(false), executor.forces)
    }

    @Test
    fun riskConfirmationRequiresANewForceLoadingFrame() = runTest {
        val executor = FakeExecutor(listOf(
            Result.failure(LogoutDataLossRiskException()),
            Result.success(LogoutOutcome.Success)
        ))
        val dispatcher = StandardTestDispatcher(testScheduler)
        val coordinator = SessionLogoutCoordinator(executor, this, dispatcher)

        coordinator.requestLogout()
        val normal = assertIs<LogoutState.AwaitingLoadingFrame>(coordinator.state.value)
        coordinator.presentLoadingFrame(normal.requestId)
        advanceUntilIdle()

        val risk = assertIs<LogoutState.AwaitingRiskConfirmation>(coordinator.state.value)
        coordinator.confirmDataLossRisk(risk.requestId)
        val force = assertIs<LogoutState.AwaitingLoadingFrame>(coordinator.state.value)
        assertTrue(force.force)
        assertEquals(listOf(false), executor.forces)

        coordinator.presentLoadingFrame(force.requestId)
        advanceUntilIdle()
        assertEquals(listOf(false, true), executor.forces)
        assertIs<LogoutState.ReadyToNavigate>(coordinator.state.value)
    }

    @Test
    fun cancellingRiskReturnsToIdle() = runTest {
        val executor = FakeExecutor(listOf(Result.failure(LogoutDataLossRiskException())))
        val dispatcher = StandardTestDispatcher(testScheduler)
        val coordinator = SessionLogoutCoordinator(executor, this, dispatcher)

        coordinator.requestLogout()
        val awaiting = assertIs<LogoutState.AwaitingLoadingFrame>(coordinator.state.value)
        coordinator.presentLoadingFrame(awaiting.requestId)
        advanceUntilIdle()
        val risk = assertIs<LogoutState.AwaitingRiskConfirmation>(coordinator.state.value)

        coordinator.cancelDataLossRisk(risk.requestId)
        assertEquals(LogoutState.Idle, coordinator.state.value)
    }

    @Test
    fun terminalLeaseCanBeReleasedAndNavigationCompletesOnHostStop() = runTest {
        val executor = FakeExecutor(listOf(Result.success(LogoutOutcome.Success)))
        val dispatcher = StandardTestDispatcher(testScheduler)
        val coordinator = SessionLogoutCoordinator(executor, this, dispatcher)
        coordinator.requestLogout()
        val awaiting = assertIs<LogoutState.AwaitingLoadingFrame>(coordinator.state.value)
        coordinator.presentLoadingFrame(awaiting.requestId)
        advanceUntilIdle()

        val firstHost = coordinator.createHostId()
        val secondHost = coordinator.createHostId()
        val firstLease = assertIs<LogoutTerminalLease.Navigation>(
            coordinator.claimTerminal(firstHost)
        )
        assertNull(coordinator.claimTerminal(secondHost))

        coordinator.releaseTerminal(firstLease)
        val secondLease = assertIs<LogoutTerminalLease.Navigation>(
            coordinator.claimTerminal(secondHost)
        )
        assertTrue(coordinator.beginNavigation(secondLease))
        assertIs<LogoutState.Navigating>(coordinator.state.value)
        assertNull(coordinator.claimTerminal(firstHost))

        coordinator.navigationHostStopped(secondLease)
        assertEquals(LogoutState.Idle, coordinator.state.value)
    }

    @Test
    fun navigationFailureReleasesReadyStateForAnotherHost() = runTest {
        val executor = FakeExecutor(listOf(Result.success(LogoutOutcome.Success)))
        val dispatcher = StandardTestDispatcher(testScheduler)
        val coordinator = SessionLogoutCoordinator(executor, this, dispatcher)
        coordinator.requestLogout()
        val awaiting = assertIs<LogoutState.AwaitingLoadingFrame>(coordinator.state.value)
        coordinator.presentLoadingFrame(awaiting.requestId)
        advanceUntilIdle()

        val lease = assertIs<LogoutTerminalLease.Navigation>(
            coordinator.claimTerminal(coordinator.createHostId())
        )
        assertTrue(coordinator.beginNavigation(lease))
        coordinator.navigationFailed(lease)

        val ready = assertIs<LogoutState.ReadyToNavigate>(coordinator.state.value)
        assertNull(ready.claimedBy)
        assertNotNull(coordinator.claimTerminal(coordinator.createHostId()))
    }

    @Test
    fun failureLeaseIsSingleConsumerAndAcknowledgesToIdle() = runTest {
        val executor = FakeExecutor(listOf(Result.failure(IllegalStateException("failed"))))
        val dispatcher = StandardTestDispatcher(testScheduler)
        val coordinator = SessionLogoutCoordinator(executor, this, dispatcher)
        coordinator.requestLogout()
        val awaiting = assertIs<LogoutState.AwaitingLoadingFrame>(coordinator.state.value)
        coordinator.presentLoadingFrame(awaiting.requestId)
        advanceUntilIdle()

        val first = coordinator.createHostId()
        val lease = assertIs<LogoutTerminalLease.Failure>(coordinator.claimTerminal(first))
        assertNull(coordinator.claimTerminal(coordinator.createHostId()))
        coordinator.failureHandled(lease)
        assertEquals(LogoutState.Idle, coordinator.state.value)
    }

    @Test
    fun riskDialogLeaseRejectsOtherHostsAndCanBeReleased() = runTest {
        val executor = FakeExecutor(listOf(Result.failure(LogoutDataLossRiskException())))
        val dispatcher = StandardTestDispatcher(testScheduler)
        val coordinator = SessionLogoutCoordinator(executor, this, dispatcher)
        coordinator.requestLogout()
        val awaiting = assertIs<LogoutState.AwaitingLoadingFrame>(coordinator.state.value)
        coordinator.presentLoadingFrame(awaiting.requestId)
        advanceUntilIdle()
        val risk = assertIs<LogoutState.AwaitingRiskConfirmation>(coordinator.state.value)
        val first = coordinator.createHostId()
        val second = coordinator.createHostId()

        assertTrue(coordinator.claimRiskDialog(risk.requestId, first))
        assertFalse(coordinator.claimRiskDialog(risk.requestId, second))
        coordinator.releaseRiskDialog(risk.requestId, first)
        assertTrue(coordinator.claimRiskDialog(risk.requestId, second))
    }

    @Test
    fun loadingHostLeaseIsExclusiveAndTransfersDuringExecution() = runTest {
        val executor = SuspendingExecutor()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val coordinator = SessionLogoutCoordinator(executor, this, dispatcher)
        coordinator.requestLogout()
        val awaiting = assertIs<LogoutState.AwaitingLoadingFrame>(coordinator.state.value)
        val first = coordinator.createHostId()
        val second = coordinator.createHostId()

        assertTrue(coordinator.claimLoadingHost(awaiting.requestId, first))
        assertFalse(coordinator.claimLoadingHost(awaiting.requestId, second))
        coordinator.onLoadingFramePresented(awaiting.requestId, second)
        assertEquals(0, executor.callCount)

        coordinator.onLoadingFramePresented(awaiting.requestId, first)
        runCurrent()
        assertEquals(1, executor.callCount)
        assertFalse(coordinator.claimLoadingHost(awaiting.requestId, second))

        coordinator.releaseLoadingHost(awaiting.requestId, first)
        assertTrue(coordinator.claimLoadingHost(awaiting.requestId, second))
        executor.complete(Result.success(LogoutOutcome.Success))
        advanceUntilIdle()
        assertIs<LogoutState.ReadyToNavigate>(coordinator.state.value)
    }

    private fun SessionLogoutCoordinator.presentLoadingFrame(requestId: Long) {
        val hostId = createHostId()
        assertTrue(claimLoadingHost(requestId, hostId))
        onLoadingFramePresented(requestId, hostId)
    }

    private class FakeExecutor(results: List<Result<LogoutOutcome>>) : SessionLogoutExecutor {
        private val queuedResults = ArrayDeque(results)
        val forces = mutableListOf<Boolean>()

        override suspend fun execute(force: Boolean): Result<LogoutOutcome> {
            forces += force
            return queuedResults.removeFirst()
        }
    }

    private class SuspendingExecutor : SessionLogoutExecutor {
        private val result = kotlinx.coroutines.CompletableDeferred<Result<LogoutOutcome>>()
        var callCount = 0

        override suspend fun execute(force: Boolean): Result<LogoutOutcome> {
            callCount += 1
            return result.await()
        }

        fun complete(value: Result<LogoutOutcome>) {
            result.complete(value)
        }
    }
}
