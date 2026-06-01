package com.chen.memorizewords.domain.account

interface UserScopedDataResetContributor {
    suspend fun clearUserScopedData()
}
