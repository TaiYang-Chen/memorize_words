package com.chen.memorizewords.domain.account.repository

interface UserScopedDataCleaner {
    suspend fun clearUserScopedData()
}

