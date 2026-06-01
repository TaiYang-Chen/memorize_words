package com.chen.memorizewords.data.sync.remoteapi.api

import com.chen.memorizewords.core.network.http.NetworkRequestExecutor
import com.chen.memorizewords.core.network.http.NetworkResult
import com.chen.memorizewords.domain.account.auth.AccessTokenState
import com.chen.memorizewords.domain.account.auth.TokenProvider
import com.chen.memorizewords.domain.sync.service.AuthenticatedRequestSuccessReporter
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException

@Singleton
class DataSyncNetworkRequestExecutor @Inject constructor(
    private val tokenProvider: TokenProvider,
    private val authenticatedRequestSuccessReporter: AuthenticatedRequestSuccessReporter
) : NetworkRequestExecutor {

    override
    suspend fun <T> executePublic(block: suspend () -> NetworkResult<T>): NetworkResult<T> {
        return try {
            block()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (t: Throwable) {
            NetworkResult.Failure.NetworkError(t)
        }
    }

    override
    suspend fun <T> executeAuthenticated(block: suspend () -> NetworkResult<T>): NetworkResult<T> {
        return when (val tokenState = tokenProvider.resolveAccessTokenState()) {
            is AccessTokenState.Available -> {
                val result = executePublic(block)
                if (result is NetworkResult.Success) {
                    authenticatedRequestSuccessReporter.onAuthenticatedRequestSucceeded()
                }
                result
            }
            is AccessTokenState.TemporarilyUnavailable ->
                NetworkResult.Failure.NetworkError(tokenState.cause)

            AccessTokenState.InvalidSession,
            AccessTokenState.NoSession ->
                NetworkResult.Failure.Unauthorized(message = "No authenticated session")
        }
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
