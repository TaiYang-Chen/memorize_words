package com.chen.memorizewords.core.network.http

interface NetworkRequestExecutor {
    suspend fun <T> executePublic(block: suspend () -> NetworkResult<T>): NetworkResult<T>
    suspend fun <T> executeAuthenticated(block: suspend () -> NetworkResult<T>): NetworkResult<T>
}
