package com.chen.memorizewords.core.network.remote

import com.chen.memorizewords.core.network.http.NetworkResult
import java.net.SocketTimeoutException

fun mapFailureToException(failure: NetworkResult.Failure): Throwable {
    return when (failure) {
        is NetworkResult.Failure.HttpError ->
            HttpStatusException(
                code = failure.code,
                businessCode = failure.businessCode,
                retryAfterSeconds = failure.retryAfterSeconds,
                resetAtMs = failure.resetAtMs,
                serverTimeMs = failure.serverTimeMs,
                traceId = failure.traceId,
                message = failure.message
            )

        is NetworkResult.Failure.Unauthorized ->
            UnauthorizedNetworkException(failure.message)

        is NetworkResult.Failure.NetworkError ->
            when (failure.throwable) {
                is SocketTimeoutException -> Exception("Connection timed out. Please check the network and retry.")
                else -> failure.throwable
            }

        is NetworkResult.Failure.GenericError ->
            Exception(failure.message)
    }
}
