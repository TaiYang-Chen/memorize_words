package com.chen.memorizewords.data.account.session

interface SessionRefreshDataSource {
    suspend fun refreshToken(refreshToken: String): SessionRefreshResult
}
