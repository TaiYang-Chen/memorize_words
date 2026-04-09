package com.chen.memorizewords.domain.model.common

data class PageSlice<T>(
    val items: List<T>,
    val hasNext: Boolean
)
