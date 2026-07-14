package com.chen.memorizewords.core.network.http

import retrofit2.Call

interface NetworkRequestExecutor {
    suspend fun <T> executePublic(block: suspend () -> NetworkResult<T>): NetworkResult<T>

    suspend fun <T> executeAuthenticated(
        block: suspend () -> NetworkResult<T>
    ): NetworkResult<T>

    /**
     * Executes an authenticated request whose success must not start failure-queue recovery.
     *
     * This is reserved for session-ending requests such as logout and account deletion, where
     * starting uploads immediately before the local session is cleared would be incorrect.
     */
    suspend fun <T> executeAuthenticatedWithoutRecovery(
        block: suspend () -> NetworkResult<T>
    ): NetworkResult<T>

    suspend fun <T> executeAuthenticated(
        call: Call<ApiResponse<T>>,
        bodyPolicy: BodyPolicy = BodyPolicy.RequireBody,
        latestDedupeKey: String? = null
    ): NetworkResult<T>

    suspend fun <T> executeQueuedRetry(
        call: Call<ApiResponse<T>>,
        bodyPolicy: BodyPolicy = BodyPolicy.RequireBody
    ): NetworkResult<T>
}
