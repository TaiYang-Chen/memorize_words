package com.chen.memorizewords.core.network.remote

class HttpStatusException(
    val code: Int,
    message: String?,
    val businessCode: String? = null,
    val retryAfterSeconds: Long? = null,
    val resetAtMs: Long? = null,
    val serverTimeMs: Long? = null,
    val traceId: String? = null
) : Exception(message)

class UnauthorizedNetworkException(message: String?) : Exception(message)
