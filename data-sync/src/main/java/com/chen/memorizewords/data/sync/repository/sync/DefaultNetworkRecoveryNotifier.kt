package com.chen.memorizewords.data.sync.repository.sync

import android.util.Log
import com.chen.memorizewords.core.common.coroutines.ApplicationScope
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Singleton
class DefaultNetworkRecoveryNotifier @Inject constructor(
    @param:ApplicationScope private val applicationScope: CoroutineScope,
    private val retryEngineProvider: Provider<FailedSyncRetryEngine>,
    private val scheduler: FailedSyncScheduler,
    private val pendingSignal: FailedEventPendingSignal
) : NetworkRecoveryNotifier {
    private val drainRunner = RecoveryDrainRunner(applicationScope) {
        try {
            retryEngineProvider.get().drain(recovery = true)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            Log.e(TAG, "failure_queue in_process_recovery_failed", failure)
        }
    }

    override fun onNormalRequestSucceeded() {
        if (!pendingSignal.hasPending()) return
        drainRunner.request()
        try {
            scheduler.scheduleRecovery()
        } catch (failure: Exception) {
            Log.e(TAG, "failure_queue recovery_schedule_failed", failure)
        }
    }

    override fun onSessionInvalidated() {
        drainRunner.cancel()
    }

    private companion object {
        const val TAG = "NetworkRecovery"
    }
}

internal class RecoveryDrainRunner(
    private val scope: CoroutineScope,
    private val drain: suspend () -> Unit
) {
    private val running = AtomicBoolean(false)
    private val recoveryRequested = AtomicBoolean(false)
    @Volatile
    private var job: Job? = null

    fun request() {
        recoveryRequested.set(true)
        startIfNeeded()
    }

    fun cancel() {
        recoveryRequested.set(false)
        job?.cancel()
    }

    private fun startIfNeeded() {
        if (!running.compareAndSet(false, true)) return
        job = scope.launch {
            try {
                while (recoveryRequested.getAndSet(false)) {
                    drain()
                }
            } finally {
                job = null
                running.set(false)
                if (recoveryRequested.get()) startIfNeeded()
            }
        }
    }
}
