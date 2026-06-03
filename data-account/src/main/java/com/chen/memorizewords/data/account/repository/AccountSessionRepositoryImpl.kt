package com.chen.memorizewords.data.account.repository

import com.chen.memorizewords.data.account.session.AuthSession
import com.chen.memorizewords.data.account.session.SessionManager
import com.chen.memorizewords.domain.account.model.AccountSession
import com.chen.memorizewords.domain.account.repository.AccountSessionRepository
import javax.inject.Inject

class AccountSessionRepositoryImpl @Inject constructor(
    private val sessionManager: SessionManager
) : AccountSessionRepository {
    override suspend fun saveSession(session: AccountSession) {
        sessionManager.save(
            AuthSession(
                accessToken = session.accessToken,
                refreshToken = session.refreshToken,
                expiresAt = session.expiresAtEpochMillis
            )
        )
    }

    override suspend fun clearSession() {
        sessionManager.clear()
    }
}

