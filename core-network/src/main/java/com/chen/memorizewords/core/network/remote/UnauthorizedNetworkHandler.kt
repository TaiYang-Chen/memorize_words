package com.chen.memorizewords.core.network.remote

interface UnauthorizedNetworkHandler {
    suspend fun handleUnauthorized()
}
