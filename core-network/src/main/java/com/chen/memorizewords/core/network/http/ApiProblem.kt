package com.chen.memorizewords.core.network.http

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = false)
data class ApiProblem(
    val type: String? = null,
    val title: String? = null,
    val status: Int? = null,
    val detail: String? = null,
    val instance: String? = null,
    val code: String? = null,
    val retryAfterSeconds: Long? = null,
    val resetAtMs: Long? = null,
    val serverTimeMs: Long? = null,
    val traceId: String? = null
)
