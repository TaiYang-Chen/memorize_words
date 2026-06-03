package com.chen.memorizewords.domain.account.repository

import com.chen.memorizewords.domain.account.model.AccountSession

interface AccountSessionRepository {
    suspend fun saveSession(session: AccountSession)

    suspend fun clearSession()
}

