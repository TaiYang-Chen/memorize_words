package com.chen.memorizewords.data.sync.repository.sync

import android.util.Log
import com.chen.memorizewords.data.sync.local.room.model.sync.FailedSyncState
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import retrofit2.Call
import retrofit2.Invocation

@Singleton
class DefaultFailedRequestRecorder @Inject constructor(
    private val mapper: FailureEventMapper,
    private val store: FailedSyncEventStore,
    private val scheduler: FailedSyncScheduler,
    private val sessionGate: FailureQueueSessionGate,
    private val pendingSignal: FailedEventPendingSignal
) : FailedRequestRecorder {
    override suspend fun record(
        call: Call<*>,
        failure: IOException,
        sessionToken: FailureQueueSessionToken
    ): FailureRecordResult {
        val invocation = call.request().tag(Invocation::class.java)
        if (invocation == null) {
            Log.w(TAG, "failure_queue skipped=no_invocation error=${failure.javaClass.simpleName}")
            return FailureRecordResult.SKIPPED_NO_INVOCATION
        }
        val event = mapper.map(invocation)
        if (event == null) {
            val method = invocation.method()
            Log.w(
                TAG,
                "failure_queue skipped=no_annotation method=${method.declaringClass.name}.${method.name}"
            )
            return FailureRecordResult.SKIPPED_NO_ANNOTATION
        }
        return sessionGate.withCurrentSession(sessionToken) {
            try {
                store.save(event)
                if (event.initialState != FailedSyncState.BLOCKED) {
                    pendingSignal.markPending()
                    try {
                        scheduler.scheduleRetryAt(System.currentTimeMillis() + FIRST_RETRY_DELAY_MS)
                    } catch (scheduleFailure: Exception) {
                        Log.e(
                            TAG,
                            "failure_queue schedule_failed eventType=${event.eventType}",
                            scheduleFailure
                        )
                    }
                }
                FailureRecordResult.RECORDED
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (persistFailure: Exception) {
                Log.e(
                    TAG,
                    "failure_queue persist_failed eventType=${event.eventType} " +
                        "error=${failure.javaClass.simpleName}",
                    persistFailure
                )
                FailureRecordResult.FAILED_TO_PERSIST
            }
        } ?: FailureRecordResult.SKIPPED_SESSION_INVALID
    }

    private companion object {
        const val TAG = "FailedRequestRecorder"
        const val FIRST_RETRY_DELAY_MS = 30_000L
    }
}
