package com.chen.memorizewords.data.remote

import com.chen.memorizewords.network.util.NetworkResult
import java.net.SocketTimeoutException

// Convert NetworkResult.Failure to Exception to fit kotlin.Result.failure.
fun mapFailureToException(failure: NetworkResult.Failure): Throwable {
    return when (failure) {
        is NetworkResult.Failure.HttpError ->
            HttpStatusException(failure.code, failure.message)

        is NetworkResult.Failure.Unauthorized ->
            UnauthorizedException(failure.message)

        is NetworkResult.Failure.NetworkError ->
            when (failure.throwable) {
                is SocketTimeoutException -> Exception("连接超时，请检查网络后重试")
                else -> failure.throwable
            }

        is NetworkResult.Failure.GenericError ->
            Exception(failure.message)
    }
}
