package com.chen.memorizewords.core.network

import java.util.concurrent.TimeUnit

data class CoreNetworkConfig(
    val baseUrl: String,
    val timeoutSeconds: Long = DEFAULT_TIMEOUT_SECONDS,
    val connectionPoolMaxIdle: Int = DEFAULT_CONNECTION_POOL_MAX_IDLE,
    val connectionPoolKeepAliveMinutes: Long = DEFAULT_CONNECTION_POOL_KEEP_ALIVE_MINUTES,
    val enableBodyLogging: Boolean = false
) {
    init {
        require(baseUrl.isNotBlank()) { "baseUrl must not be blank" }
    }

    internal val timeoutUnit: TimeUnit = TimeUnit.SECONDS

    private companion object {
        const val DEFAULT_TIMEOUT_SECONDS = 30L
        const val DEFAULT_CONNECTION_POOL_MAX_IDLE = 5
        const val DEFAULT_CONNECTION_POOL_KEEP_ALIVE_MINUTES = 5L
    }
}
