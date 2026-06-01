package com.chen.memorizewords.core.network.remote

class HttpStatusException(
    val code: Int,
    message: String?
) : Exception(message)

class UnauthorizedNetworkException(message: String?) : Exception(message)
