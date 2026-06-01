package com.chen.memorizewords.core.common.paging

data class PageSlice<T>(
    val items: List<T>,
    val hasNext: Boolean
)
