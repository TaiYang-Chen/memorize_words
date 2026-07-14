package com.chen.memorizewords.data.sync.repository.sync

import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import javax.inject.Provider
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking

class NetworkRecoveryNotifierTest {
    @Test
    fun `success with no pending signal does not schedule recovery`() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val signal = FailedEventPendingSignal().apply { initialize(hasPending = false) }
        val scheduler = RecordingScheduler()
        val notifier = DefaultNetworkRecoveryNotifier(
            applicationScope = scope,
            retryEngineProvider = Provider { error("retry engine must not be requested") },
            scheduler = scheduler,
            pendingSignal = signal
        )

        try {
            notifier.onNormalRequestSucceeded()

            assertEquals(0, scheduler.recoveryCalls)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `success during drain schedules another pass`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val calls = AtomicInteger(0)
        val firstStarted = CompletableDeferred<Unit>()
        val allowFirstToFinish = CompletableDeferred<Unit>()
        val secondStarted = CompletableDeferred<Unit>()
        val runner = RecoveryDrainRunner(scope) {
            when (calls.incrementAndGet()) {
                1 -> {
                    firstStarted.complete(Unit)
                    allowFirstToFinish.await()
                }
                2 -> secondStarted.complete(Unit)
            }
        }

        try {
            runner.request()
            firstStarted.await()
            runner.request()
            allowFirstToFinish.complete(Unit)
            secondStarted.await()

            assertEquals(2, calls.get())
        } finally {
            scope.cancel()
        }
    }
}

private class RecordingScheduler : FailedSyncScheduler {
    var recoveryCalls: Int = 0

    override fun scheduleDrain() = Unit

    override fun scheduleContinuation() = Unit

    override fun scheduleRecovery() {
        recoveryCalls++
    }

    override fun scheduleRetryAt(nextAttemptAtMs: Long) = Unit

    override fun ensurePeriodic() = Unit

    override fun cancelAll() = Unit
}
