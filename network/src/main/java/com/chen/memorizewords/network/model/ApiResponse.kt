package com.chen.memorizewords.network.model

import com.squareup.moshi.JsonClass

/**
 * 鍚庣缁熶竴杩斿洖缁撴瀯
 */
@JsonClass(generateAdapter = false)
data class ApiResponse<T>(
    val data: T?,
    val code: Int,
    val message: String
)
