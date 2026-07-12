package com.chen.memorizewords.core.network

import java.util.concurrent.TimeUnit

data class CoreNetworkConfig(
    val baseUrl: String,
    val connectTimeoutSeconds: Long = DEFAULT_CONNECT_TIMEOUT_SECONDS,
    val readTimeoutSeconds: Long = DEFAULT_READ_TIMEOUT_SECONDS,
    val writeTimeoutSeconds: Long = DEFAULT_WRITE_TIMEOUT_SECONDS,
    val connectionPoolMaxIdle: Int = DEFAULT_CONNECTION_POOL_MAX_IDLE,
    val connectionPoolKeepAliveMinutes: Long = DEFAULT_CONNECTION_POOL_KEEP_ALIVE_MINUTES,
    val enableBodyLogging: Boolean = false
) {
    init {
        require(baseUrl.isNotBlank()) { "baseUrl must not be blank" }
    }

    internal val timeoutUnit: TimeUnit = TimeUnit.SECONDS

    private companion object {
        const val DEFAULT_CONNECT_TIMEOUT_SECONDS = 5L
        const val DEFAULT_READ_TIMEOUT_SECONDS = 15L
        const val DEFAULT_WRITE_TIMEOUT_SECONDS = 15L
        const val DEFAULT_CONNECTION_POOL_MAX_IDLE = 5
        const val DEFAULT_CONNECTION_POOL_KEEP_ALIVE_MINUTES = 5L
    }
}
