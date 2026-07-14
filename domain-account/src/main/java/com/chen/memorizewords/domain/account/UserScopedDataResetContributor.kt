package com.chen.memorizewords.domain.account

interface UserScopedDataResetContributor {
    val resetPriority: Int
        get() = 0

    suspend fun clearUserScopedData()
}
