package com.chen.memorizewords.data.account.repository

import com.chen.memorizewords.data.account.session.LocalUserDataOwnerDataSource
import com.chen.memorizewords.domain.account.repository.UserDataOwnerRepository
import javax.inject.Inject

class UserDataOwnerRepositoryImpl @Inject constructor(
    private val localUserDataOwnerDataSource: LocalUserDataOwnerDataSource
) : UserDataOwnerRepository {
    override suspend fun getOwnerUserId(): Long? {
        return localUserDataOwnerDataSource.getOwnerUserId()
    }

    override suspend fun saveOwnerUserId(userId: Long) {
        localUserDataOwnerDataSource.saveOwnerUserId(userId)
    }

    override suspend fun clearOwnerUserId() {
        localUserDataOwnerDataSource.clearOwnerUserId()
    }
}

