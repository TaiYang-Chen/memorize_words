package com.chen.memorizewords.domain.account.repository

interface UserDataOwnerRepository {
    suspend fun getOwnerUserId(): Long?

    suspend fun saveOwnerUserId(userId: Long)

    suspend fun clearOwnerUserId()
}

