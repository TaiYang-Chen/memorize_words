package com.chen.memorizewords.network.util

import com.chen.memorizewords.network.model.ApiResponse
import com.chen.memorizewords.network.policy.NetworkRoutePolicy

sealed interface BodyPolicy {
    data object RequireBody : BodyPolicy
    data object AllowNullBody : BodyPolicy
}

object ApiResponseParser {

    fun httpFailure(code: Int, message: String?): NetworkResult.Failure.HttpError {
        return NetworkResult.Failure.HttpError(
            code = code,
            message = message?.takeIf { it.isNotBlank() } ?: "HTTP Error $code"
        )
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> parseRequired(
        apiResponse: ApiResponse<T>?,
        encodedPath: String,
        successTypeIsUnit: Boolean
    ): NetworkResult<T> {
        val response = apiResponse ?: return NetworkResult.Failure.GenericError("Response body is null")
        return when (response.code) {
            200 -> when {
                response.data != null -> NetworkResult.Success(response.data)
                successTypeIsUnit -> NetworkResult.Success(Unit as T)
                else -> NetworkResult.Failure.GenericError("Response data is null")
            }

            401 -> businessUnauthorized(response = response, encodedPath = encodedPath)
            else -> NetworkResult.Failure.HttpError(response.code, response.message)
        }
    }

    fun <T> parseNullable(
        apiResponse: ApiResponse<T?>?,
        encodedPath: String
    ): NetworkResult<T?> {
        val response = apiResponse ?: return NetworkResult.Failure.GenericError("Response body is null")
        return when (response.code) {
            200 -> NetworkResult.Success(response.data)
            401 -> businessUnauthorized(response = response, encodedPath = encodedPath)
            else -> NetworkResult.Failure.HttpError(response.code, response.message)
        }
    }

    private fun businessUnauthorized(
        response: ApiResponse<*>,
        encodedPath: String
    ): NetworkResult.Failure {
        return if (NetworkRoutePolicy.shouldTreatUnauthorizedAsHttpError(encodedPath)) {
            NetworkResult.Failure.HttpError(response.code, response.message)
        } else {
            NetworkResult.Failure.Unauthorized(
                code = response.code,
                message = response.message,
                path = encodedPath
            )
        }
    }
}
