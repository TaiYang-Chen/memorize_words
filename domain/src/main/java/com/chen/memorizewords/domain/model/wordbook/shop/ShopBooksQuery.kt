package com.chen.memorizewords.domain.model.wordbook.shop

data class ShopBooksQuery(
    val pageIndex: Int,
    val pageSize: Int,
    val category: String = "",
    val keyword: String = ""
)
