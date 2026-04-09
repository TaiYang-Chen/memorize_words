package com.chen.memorizewords.data.session

import kotlinx.coroutines.flow.Flow

interface SessionLocalDataSource {
    fun saveSession(session: AuthSession)
    fun getSession(): AuthSession?
    fun observeSession(): Flow<AuthSession?>
    fun clear()
}
