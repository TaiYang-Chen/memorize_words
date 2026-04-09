package com.chen.memorizewords.data.remote

import com.chen.memorizewords.data.session.UnauthorizedSessionHandler
import com.chen.memorizewords.network.util.NetworkResult
import javax.inject.Inject
import kotlinx.coroutines.CancellationException

class RemoteResultAdapter @Inject constructor(
    private val unauthorizedSessionHandler: UnauthorizedSessionHandler
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
                    unauthorizedSessionHandler.handleUnauthorized()
                }
                Result.failure(mapFailureToException(result))
            }
        }
    }
}
