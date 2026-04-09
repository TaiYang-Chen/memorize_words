package com.chen.memorizewords.network.api

import com.chen.memorizewords.domain.auth.AccessTokenState
import com.chen.memorizewords.domain.auth.TokenProvider
import com.chen.memorizewords.network.util.NetworkResult
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException

@Singleton
class NetworkRequestExecutor @Inject constructor(
    private val tokenProvider: TokenProvider
) {

    suspend fun <T> executePublic(block: suspend () -> NetworkResult<T>): NetworkResult<T> {
        return try {
            block()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (t: Throwable) {
            NetworkResult.Failure.NetworkError(t)
        }
    }

    suspend fun <T> executeAuthenticated(block: suspend () -> NetworkResult<T>): NetworkResult<T> {
        return when (val tokenState = tokenProvider.resolveAccessTokenState()) {
            is AccessTokenState.Available -> executePublic(block)
            is AccessTokenState.TemporarilyUnavailable ->
                NetworkResult.Failure.NetworkError(tokenState.cause)

            AccessTokenState.InvalidSession,
            AccessTokenState.NoSession ->
                NetworkResult.Failure.Unauthorized(message = "No authenticated session")
        }
    }
}
