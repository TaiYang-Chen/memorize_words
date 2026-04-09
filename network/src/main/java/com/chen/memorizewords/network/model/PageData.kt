package com.chen.memorizewords.network.model

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = false)
data class PageData<T>(
    val items: List<T>,
    val page: Int,
    val size: Int,
    val total: Long
)
