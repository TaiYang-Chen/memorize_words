package com.chen.memorizewords.data.session

interface SessionRefreshDataSource {
    suspend fun refreshToken(refreshToken: String): SessionRefreshResult
}
