package com.chen.memorizewords.core.network.http

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = false)
data class ApiResponse<T>(
    val data: T?,
    val code: Int,
    val message: String
)

@JsonClass(generateAdapter = false)
data class PageData<T>(
    val items: List<T>,
    val page: Int,
    val size: Int,
    val total: Long
)
