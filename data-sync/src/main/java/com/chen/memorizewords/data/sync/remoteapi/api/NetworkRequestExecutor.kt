package com.chen.memorizewords.data.sync.remoteapi.api

import android.util.Log
import com.chen.memorizewords.core.network.http.ApiResponse
import com.chen.memorizewords.core.network.http.BodyPolicy
import com.chen.memorizewords.core.network.http.NetworkRequestExecutor
import com.chen.memorizewords.core.network.http.NetworkResult
import com.chen.memorizewords.core.network.http.awaitApiResponse
import com.chen.memorizewords.data.sync.repository.sync.FailedRequestRecorder
import com.chen.memorizewords.data.sync.repository.sync.FailureQueueSessionGate
import com.chen.memorizewords.data.sync.repository.sync.FailureQueueSessionToken
import com.chen.memorizewords.data.sync.repository.sync.LatestSyncRequestCoordinator
import com.chen.memorizewords.data.sync.repository.sync.NetworkRecoveryNotifier
import com.chen.memorizewords.domain.account.auth.AccessTokenState
import com.chen.memorizewords.domain.account.auth.TokenProvider
import com.squareup.moshi.JsonEncodingException
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import retrofit2.Call

@Singleton
class DataSyncNetworkRequestExecutor @Inject constructor(
    private val tokenProvider: TokenProvider,
    private val failedRequestRecorder: FailedRequestRecorder,
    private val networkRecoveryNotifier: NetworkRecoveryNotifier,
    private val latestSyncRequestCoordinator: LatestSyncRequestCoordinator,
    private val failureQueueSessionGate: FailureQueueSessionGate
) : NetworkRequestExecutor {

    override suspend fun <T> executePublic(block: suspend () -> NetworkResult<T>): NetworkResult<T> {
        return executeBlock(block, notifySuccess = true)
    }

    private suspend fun <T> executeBlock(
        block: suspend () -> NetworkResult<T>,
        notifySuccess: Boolean
    ): NetworkResult<T> {
        val result = try {
            block()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            NetworkResult.Failure.NetworkError(failure)
        }
        if (result is NetworkResult.Success && notifySuccess) notifyRecovery()
        return result
    }

    override suspend fun <T> executeAuthenticated(
        block: suspend () -> NetworkResult<T>
    ): NetworkResult<T> = executeAuthenticatedBlock(block, notifySuccess = true)

    override suspend fun <T> executeAuthenticatedWithoutRecovery(
        block: suspend () -> NetworkResult<T>
    ): NetworkResult<T> = executeAuthenticatedBlock(block, notifySuccess = false)

    private suspend fun <T> executeAuthenticatedBlock(
        block: suspend () -> NetworkResult<T>,
        notifySuccess: Boolean
    ): NetworkResult<T> {
        return when (val tokenState = tokenProvider.resolveAccessTokenState()) {
            is AccessTokenState.Available -> {
                val sessionToken = failureQueueSessionGate.capture()
                val result = executeBlock(block, notifySuccess = false)
                if (
                    result is NetworkResult.Success &&
                    (sessionToken == null || !failureQueueSessionGate.isCurrent(sessionToken))
                ) {
                    sessionChangedFailure()
                } else {
                    if (result is NetworkResult.Success && notifySuccess) {
                        notifyRecovery()
                    }
                    result
                }
            }
            is AccessTokenState.TemporarilyUnavailable ->
                NetworkResult.Failure.NetworkError(tokenState.cause)

            AccessTokenState.InvalidSession,
            AccessTokenState.NoSession ->
                NetworkResult.Failure.Unauthorized(message = "No authenticated session")
        }
    }

    override suspend fun <T> executeAuthenticated(
        call: Call<ApiResponse<T>>,
        bodyPolicy: BodyPolicy,
        latestDedupeKey: String?
    ): NetworkResult<T> {
        return when (val tokenState = tokenProvider.resolveAccessTokenState()) {
            is AccessTokenState.Available -> {
                val sessionToken = failureQueueSessionGate.capture()
                val result = latestSyncRequestCoordinator.executeNormal(latestDedupeKey) {
                    executeCall(
                        call = call,
                        bodyPolicy = bodyPolicy,
                        failureSessionToken = sessionToken
                    )
                }
                if (
                    result is NetworkResult.Success &&
                    (sessionToken == null || !failureQueueSessionGate.isCurrent(sessionToken))
                ) {
                    sessionChangedFailure()
                } else {
                    if (result is NetworkResult.Success) notifyRecovery()
                    result
                }
            }
            is AccessTokenState.TemporarilyUnavailable -> NetworkResult.Failure.NetworkError(tokenState.cause)
            AccessTokenState.InvalidSession,
            AccessTokenState.NoSession -> NetworkResult.Failure.Unauthorized(message = "No authenticated session")
        }
    }

    override suspend fun <T> executeQueuedRetry(
        call: Call<ApiResponse<T>>,
        bodyPolicy: BodyPolicy
    ): NetworkResult<T> {
        return when (val tokenState = tokenProvider.resolveAccessTokenState()) {
            is AccessTokenState.Available -> executeCall(
                call = call,
                bodyPolicy = bodyPolicy,
                failureSessionToken = null
            )
            is AccessTokenState.TemporarilyUnavailable -> NetworkResult.Failure.NetworkError(tokenState.cause)
            AccessTokenState.InvalidSession,
            AccessTokenState.NoSession -> NetworkResult.Failure.Unauthorized(message = "No authenticated session")
        }
    }

    private suspend fun <T> executeCall(
        call: Call<ApiResponse<T>>,
        bodyPolicy: BodyPolicy,
        failureSessionToken: FailureQueueSessionToken?
    ): NetworkResult<T> {
        val result = try {
            call.awaitApiResponse(bodyPolicy)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            NetworkResult.Failure.NetworkError(failure)
        }
        val ioFailure = (result as? NetworkResult.Failure.NetworkError)
            ?.throwable
            ?.queueableTransportIOException()
        if (
            failureSessionToken != null &&
            ioFailure != null &&
            call.isExecuted &&
            !call.isCanceled &&
            currentCoroutineContext().isActive
        ) {
            try {
                failedRequestRecorder.record(call, ioFailure, failureSessionToken)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (recordFailure: Exception) {
                Log.e(TAG, "failure_queue recorder_failed", recordFailure)
            }
        }
        return result
    }

    private fun notifyRecovery() {
        try {
            networkRecoveryNotifier.onNormalRequestSucceeded()
        } catch (failure: Exception) {
            Log.e(TAG, "failure_queue recovery_notification_failed", failure)
        }
    }

    private fun <T> sessionChangedFailure(): NetworkResult<T> {
        return NetworkResult.Failure.Unauthorized(message = "Authenticated session changed")
    }

    private fun Throwable.queueableTransportIOException(): IOException? {
        val ioFailure = this as? IOException ?: return null
        val isResponseDecodingFailure = generateSequence<Throwable>(ioFailure) { it.cause }
            .any { it is JsonEncodingException }
        return if (isResponseDecodingFailure) null else ioFailure
    }

    private companion object {
        const val TAG = "NetworkRequestExecutor"
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class NetworkRequestExecutorModule {
    @Binds
    @Singleton
    abstract fun bindNetworkRequestExecutor(
        impl: DataSyncNetworkRequestExecutor
    ): NetworkRequestExecutor
}
