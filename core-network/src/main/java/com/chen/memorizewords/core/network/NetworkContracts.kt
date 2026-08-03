package com.chen.memorizewords.core.network

interface AccessTokenSource {
    fun currentAccessToken(): String?
}

object CoreNetworkHeaders {
    const val SKIP_AUTHORIZATION = "X-Skip-Authorization"
}
