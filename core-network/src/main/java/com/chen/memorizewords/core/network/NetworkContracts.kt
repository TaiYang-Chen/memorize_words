package com.chen.memorizewords.core.network

data class ApiEnvelope<T>(
    val code: Int,
    val message: String?,
    val data: T?
)

sealed interface NetworkCallResult<out T> {
    data class Success<T>(val value: T) : NetworkCallResult<T>
    data class HttpError(val code: Int, val body: String? = null) : NetworkCallResult<Nothing>
    data class NetworkError(val cause: Throwable) : NetworkCallResult<Nothing>
    data class DecodeError(val cause: Throwable) : NetworkCallResult<Nothing>
}

interface AccessTokenSource {
    fun currentAccessToken(): String?
}
