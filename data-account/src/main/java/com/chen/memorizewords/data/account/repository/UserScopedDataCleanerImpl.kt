package com.chen.memorizewords.data.account.repository

import com.chen.memorizewords.data.account.session.UserDataCleaner
import com.chen.memorizewords.domain.account.repository.UserScopedDataCleaner
import javax.inject.Inject

class UserScopedDataCleanerImpl @Inject constructor(
    private val userDataCleaner: UserDataCleaner
) : UserScopedDataCleaner {
    override suspend fun clearUserScopedData() {
        userDataCleaner.clearUserLearningData()
    }
}

