package com.chen.memorizewords.data.remote

class HttpStatusException(
    val code: Int,
    message: String?
) : Exception(message)
