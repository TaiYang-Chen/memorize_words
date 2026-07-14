package com.chen.memorizewords.data.sync.remoteapi.api

import com.chen.memorizewords.core.network.http.ApiResponse
import com.chen.memorizewords.core.network.http.BodyPolicy
import com.chen.memorizewords.core.network.http.NetworkResult
import com.chen.memorizewords.domain.account.auth.AccessTokenState
import com.chen.memorizewords.domain.account.auth.AuthStateProvider
import com.chen.memorizewords.domain.account.auth.TokenProvider
import com.chen.memorizewords.data.sync.repository.sync.FailedRequestRecorder
import com.chen.memorizewords.data.sync.repository.sync.FailureQueueSessionGate
import com.chen.memorizewords.data.sync.repository.sync.FailureQueueSessionToken
import com.chen.memorizewords.data.sync.repository.sync.FailureRecordResult
import com.chen.memorizewords.data.sync.repository.sync.NetworkRecoveryNotifier
import com.chen.memorizewords.data.sync.repository.sync.LatestSyncRequestCoordinator
import com.chen.memorizewords.data.sync.repository.sync.ReplayOutcome
import com.chen.memorizewords.data.sync.local.room.model.sync.FailedSyncEventEntity
import java.io.IOException
import com.squareup.moshi.JsonEncodingException
import okhttp3.Request
import okio.Timeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class DataSyncNetworkRequestExecutorTest {

    @Test
    fun `normal success wakes recovery but session-ending success does not`() = runBlocking {
        var wakeCount = 0
        val executor = DataSyncNetworkRequestExecutor(
            tokenProvider = AvailableTokenProvider,
            failedRequestRecorder = object : FailedRequestRecorder {
                override suspend fun record(
                    call: retrofit2.Call<*>,
                    failure: IOException,
                    sessionToken: FailureQueueSessionToken
                ) = FailureRecordResult.RECORDED
            },
            networkRecoveryNotifier = object : NetworkRecoveryNotifier {
                override fun onNormalRequestSucceeded() {
                    wakeCount++
                }
                override fun onSessionInvalidated() = Unit
            },
            latestSyncRequestCoordinator = PassthroughLatestCoordinator,
            failureQueueSessionGate = sessionGate()
        )

        executor.executeAuthenticated { NetworkResult.Success(Unit) }
        executor.executeAuthenticatedWithoutRecovery {
            NetworkResult.Success(Unit)
        }

        assertEquals(1, wakeCount)
    }

    @Test
    fun `authenticated call records only transport IOException`() = runBlocking {
        var recorded = 0
        var notified = 0
        val executor = executor(
            onRecord = { recorded++ },
            onNotify = { notified++ }
        )

        val result = executor.executeAuthenticated(
            FakeCall<Unit>(failure = IOException("offline")),
            BodyPolicy.UnitBody
        )

        assertIs<NetworkResult.Failure.NetworkError>(result)
        assertEquals(1, recorded)
        assertEquals(0, notified)
    }

    @Test
    fun `queued retry transport failure never creates another event`() = runBlocking {
        var recorded = 0
        var notified = 0
        val executor = executor(
            onRecord = { recorded++ },
            onNotify = { notified++ }
        )

        executor.executeQueuedRetry(
            FakeCall<Unit>(failure = IOException("offline")),
            BodyPolicy.UnitBody
        )

        assertEquals(0, recorded)
        assertEquals(0, notified)
    }

    @Test
    fun `business error does not enter failed queue`() = runBlocking {
        var recorded = 0
        val executor = executor(onRecord = { recorded++ })

        val result = executor.executeAuthenticated(
            FakeCall<Unit>(body = ApiResponse(data = null, code = 500, message = "failed")),
            BodyPolicy.UnitBody
        )

        assertIs<NetworkResult.Failure.HttpError>(result)
        assertEquals(0, recorded)
    }

    @Test
    fun `response decoding IOException does not enter failed queue`() = runBlocking {
        var recorded = 0
        val executor = executor(onRecord = { recorded++ })

        val result = executor.executeAuthenticated(
            FakeCall<Unit>(failure = JsonEncodingException("malformed response")),
            BodyPolicy.UnitBody
        )

        assertIs<NetworkResult.Failure.NetworkError>(result)
        assertEquals(0, recorded)
    }

    @Test
    fun `successful normal call wakes recovery once`() = runBlocking {
        var notified = 0
        val executor = executor(onNotify = { notified++ })

        val result = executor.executeAuthenticated(
            FakeCall<Unit>(body = ApiResponse(data = null, code = 200, message = "ok")),
            BodyPolicy.UnitBody
        )

        assertIs<NetworkResult.Success<Unit>>(result)
        assertEquals(1, notified)
    }

    @Test
    fun `transport failure carries session captured before call execution`() = runBlocking {
        var recordedToken: FailureQueueSessionToken? = null
        val executor = DataSyncNetworkRequestExecutor(
            tokenProvider = AvailableTokenProvider,
            failedRequestRecorder = object : FailedRequestRecorder {
                override suspend fun record(
                    call: Call<*>,
                    failure: IOException,
                    sessionToken: FailureQueueSessionToken
                ): FailureRecordResult {
                    recordedToken = sessionToken
                    return FailureRecordResult.RECORDED
                }
            },
            networkRecoveryNotifier = object : NetworkRecoveryNotifier {
                override fun onNormalRequestSucceeded() = Unit
                override fun onSessionInvalidated() = Unit
            },
            latestSyncRequestCoordinator = PassthroughLatestCoordinator,
            failureQueueSessionGate = sessionGate()
        )

        executor.executeAuthenticated(
            FakeCall<Unit>(failure = IOException("offline")),
            BodyPolicy.UnitBody
        )

        assertEquals(0L, recordedToken?.generation)
    }

    private fun executor(
        onRecord: () -> Unit = {},
        onNotify: () -> Unit = {}
    ) = DataSyncNetworkRequestExecutor(
        tokenProvider = AvailableTokenProvider,
        failedRequestRecorder = object : FailedRequestRecorder {
            override suspend fun record(
                call: Call<*>,
                failure: IOException,
                sessionToken: FailureQueueSessionToken
            ): FailureRecordResult {
                onRecord()
                return FailureRecordResult.RECORDED
            }
        },
        networkRecoveryNotifier = object : NetworkRecoveryNotifier {
            override fun onNormalRequestSucceeded() = onNotify()
            override fun onSessionInvalidated() = Unit
        },
        latestSyncRequestCoordinator = PassthroughLatestCoordinator,
        failureQueueSessionGate = sessionGate()
    )

    private object AvailableTokenProvider : TokenProvider {
        override suspend fun resolveAccessTokenState(
            notifyKickoutOnInvalidSession: Boolean
        ): AccessTokenState = AccessTokenState.Available("token")

        override fun getAccessTokenIfValid(): String = "token"
    }

    private object PassthroughLatestCoordinator : LatestSyncRequestCoordinator {
        override suspend fun <T> executeNormal(
            latestDedupeKey: String?,
            block: suspend () -> NetworkResult<T>
        ): NetworkResult<T> = block()

        override suspend fun replay(
            event: FailedSyncEventEntity,
            block: suspend () -> ReplayOutcome
        ): ReplayOutcome = block()

        override fun onSessionInvalidated() = Unit
    }

    private class FakeCall<T>(
        private val body: ApiResponse<T>? = null,
        private val failure: Throwable? = null,
        private val beforeCallback: () -> Unit = {}
    ) : Call<ApiResponse<T>> {
        private var executed = false
        private var canceled = false

        override fun enqueue(callback: Callback<ApiResponse<T>>) {
            executed = true
            beforeCallback()
            val throwable = failure
            if (throwable != null) callback.onFailure(this, throwable)
            else callback.onResponse(this, Response.success(body))
        }

        override fun isExecuted(): Boolean = executed

        override fun clone(): Call<ApiResponse<T>> = FakeCall(body, failure, beforeCallback)

        override fun isCanceled(): Boolean = canceled

        override fun cancel() {
            canceled = true
        }

        override fun execute(): Response<ApiResponse<T>> = error("not used")

        override fun request(): Request = Request.Builder()
            .url("http://localhost/test")
            .build()

        override fun timeout(): Timeout = Timeout.NONE
    }

    private fun sessionGate(): FailureQueueSessionGate {
        return FailureQueueSessionGate(AuthenticatedStateProvider)
    }

    private object AuthenticatedStateProvider : AuthStateProvider {
        override fun isAuthenticated(): Boolean = true
        override fun observeAuthenticated(): Flow<Boolean> = flowOf(true)
    }
}
