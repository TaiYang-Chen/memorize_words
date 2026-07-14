package com.chen.memorizewords.core.common.coroutines

import java.util.logging.Level
import java.util.logging.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.yield

/**
 * Launches best-effort direct uploads outside the caller's local transaction.
 *
 * Requests are globally bounded and operations sharing an ordering key enter the network path in
 * local commit order. Results are always consumed so an application-scope child cannot fail
 * silently or cancel unrelated work.
 */
class DirectSyncLauncher(
    private val applicationScope: CoroutineScope
) {
    private val inFlightPermits = Semaphore(MAX_IN_FLIGHT_REQUESTS)
    private val orderedLocks = Array(ORDERED_LOCK_STRIPES) { Mutex() }
    private val activeJobs = mutableSetOf<Job>()
    private val activeJobsLock = Any()

    fun <T> launch(
        operation: String,
        orderingKey: String? = null,
        request: suspend () -> Result<T>,
        onSuccess: suspend (T) -> Unit = {}
    ) {
        val start = if (orderingKey == null) CoroutineStart.DEFAULT else CoroutineStart.UNDISPATCHED
        synchronized(activeJobsLock) {
            val job = applicationScope.launch(start = start) {
                val block: suspend () -> Unit = {
                    inFlightPermits.withPermit {
                        execute(operation, request, onSuccess)
                    }
                }
                if (orderingKey == null) {
                    block()
                } else {
                    withOrdering(orderingKey) {
                        // Enter the mutex before returning to the caller, then yield so token lookup
                        // and request preparation never execute inline on the caller's thread.
                        yield()
                        block()
                    }
                }
            }
            activeJobs += job
            job.invokeOnCompletion {
                synchronized(activeJobsLock) {
                    activeJobs -= job
                }
            }
        }
    }

    suspend fun <T> withOrdering(
        orderingKey: String,
        block: suspend () -> T
    ): T = orderedLocks[orderedLockIndex(orderingKey)].withLock { block() }

    fun cancelAll() {
        val jobs = synchronized(activeJobsLock) {
            activeJobs.toList().also { activeJobs.clear() }
        }
        jobs.forEach { it.cancel() }
    }

    private fun orderedLockIndex(orderingKey: String): Int {
        return (orderingKey.hashCode() and Int.MAX_VALUE) % orderedLocks.size
    }

    private suspend fun <T> execute(
        operation: String,
        request: suspend () -> Result<T>,
        onSuccess: suspend (T) -> Unit
    ) {
        try {
            val value = request().getOrThrow()
            onSuccess(value)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            LOGGER.log(Level.SEVERE, "direct_sync failed operation=$operation", failure)
        }
    }

    private companion object {
        val LOGGER: Logger = Logger.getLogger(DirectSyncLauncher::class.java.name)
        const val MAX_IN_FLIGHT_REQUESTS = 4
        const val ORDERED_LOCK_STRIPES = 64
    }
}
