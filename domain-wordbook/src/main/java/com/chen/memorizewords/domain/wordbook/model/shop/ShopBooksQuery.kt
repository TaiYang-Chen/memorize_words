package com.chen.memorizewords.domain.wordbook.model.shop
data class ShopBooksQuery(
    val pageIndex: Int,
    val pageSize: Int,
    val category: String = "",
    val keyword: String = ""
)
