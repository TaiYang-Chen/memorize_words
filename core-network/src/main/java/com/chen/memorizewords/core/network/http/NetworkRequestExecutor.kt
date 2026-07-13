package com.chen.memorizewords.core.network.http

interface NetworkRequestExecutor {
    suspend fun <T> executePublic(block: suspend () -> NetworkResult<T>): NetworkResult<T>
    suspend fun <T> executeAuthenticated(
        origin: AuthenticatedRequestOrigin = AuthenticatedRequestOrigin.USER,
        block: suspend () -> NetworkResult<T>
    ): NetworkResult<T>
}

enum class AuthenticatedRequestOrigin {
    USER,
    SYNC
}
