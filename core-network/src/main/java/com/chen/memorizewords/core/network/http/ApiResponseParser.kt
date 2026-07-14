package com.chen.memorizewords.core.network.http

import com.chen.memorizewords.core.network.CoreNetworkRoutePolicy
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

sealed interface BodyPolicy {
    data object RequireBody : BodyPolicy
    data object AllowNullBody : BodyPolicy
    data object UnitBody : BodyPolicy
}

object ApiResponseParser {
    private val problemAdapter = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()
        .adapter(ApiProblem::class.java)

    fun httpFailure(
        code: Int,
        message: String?,
        errorBody: String?,
        encodedPath: String
    ): NetworkResult.Failure {
        val problem = runCatching {
            errorBody?.takeIf(String::isNotBlank)?.let(problemAdapter::fromJson)
        }.getOrNull()
        val resolvedMessage = problem?.detail?.takeIf(String::isNotBlank)
            ?: message?.takeIf(String::isNotBlank)
            ?: "HTTP Error $code"
        if (code == 401) {
            return if (CoreNetworkRoutePolicy().shouldTreatUnauthorizedAsHttpError(encodedPath)) {
                NetworkResult.Failure.HttpError(
                    code = code,
                    message = resolvedMessage,
                    businessCode = problem?.code,
                    retryAfterSeconds = problem?.retryAfterSeconds,
                    resetAtMs = problem?.resetAtMs,
                    serverTimeMs = problem?.serverTimeMs,
                    traceId = problem?.traceId
                )
            } else {
                NetworkResult.Failure.Unauthorized(code, resolvedMessage, encodedPath)
            }
        }
        return NetworkResult.Failure.HttpError(
            code = code,
            message = resolvedMessage,
            businessCode = problem?.code,
            retryAfterSeconds = problem?.retryAfterSeconds,
            resetAtMs = problem?.resetAtMs,
            serverTimeMs = problem?.serverTimeMs,
            traceId = problem?.traceId
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
        return if (CoreNetworkRoutePolicy().shouldTreatUnauthorizedAsHttpError(encodedPath)) {
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
