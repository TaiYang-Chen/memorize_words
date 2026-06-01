package com.chen.memorizewords.core.network.remote

import com.chen.memorizewords.core.network.http.NetworkResult
import javax.inject.Inject
import kotlinx.coroutines.CancellationException

class RemoteResultAdapter @Inject constructor(
    private val unauthorizedNetworkHandler: UnauthorizedNetworkHandler
) {

    suspend inline fun <T> toResult(
        block: () -> NetworkResult<T>
    ): Result<T> {
        return try {
            adapt(block())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    suspend fun <T> adapt(result: NetworkResult<T>): Result<T> {
        return when (result) {
            is NetworkResult.Success -> Result.success(result.data)
            is NetworkResult.Failure -> {
                if (result is NetworkResult.Failure.Unauthorized) {
                    unauthorizedNetworkHandler.handleUnauthorized()
                }
                Result.failure(mapFailureToException(result))
            }
        }
    }
}
