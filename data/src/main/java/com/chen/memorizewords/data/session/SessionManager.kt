package com.chen.memorizewords.data.session

import com.chen.memorizewords.domain.auth.AccessTokenState
import com.chen.memorizewords.domain.auth.TokenProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.lang.System.currentTimeMillis
import javax.inject.Provider

class SessionManager(
    private val local: SessionLocalDataSource,
    private val refreshRemoteDataSourceProvider: Provider<SessionRefreshDataSource>,
    private val localAuthSessionCleaner: LocalAuthStateCleaner
) : TokenProvider {

    private val mutex = Mutex()

    override fun getAccessTokenIfValid(): String? {
        val session = local.getSession() ?: return null
        return if (session.expiresAt > currentTimeMillis()) session.accessToken else null
    }

    override suspend fun resolveAccessTokenState(): AccessTokenState = withContext(Dispatchers.IO) {
        val cached = local.getSession() ?: return@withContext AccessTokenState.NoSession
        if (cached.expiresAt > currentTimeMillis()) {
            return@withContext AccessTokenState.Available(cached.accessToken)
        }

        mutex.withLock {
            val again = local.getSession() ?: return@withLock AccessTokenState.NoSession
            if (again.expiresAt > currentTimeMillis()) {
                return@withLock AccessTokenState.Available(again.accessToken)
            }

            when (
                val refreshResult = refreshRemoteDataSourceProvider.get()
                    .refreshToken(again.refreshToken)
            ) {
                is SessionRefreshResult.Refreshed -> {
                    local.saveSession(refreshResult.session)
                    AccessTokenState.Available(refreshResult.session.accessToken)
                }

                SessionRefreshResult.InvalidSession -> {
                    localAuthSessionCleaner.clearLocalAuthState()
                    AccessTokenState.InvalidSession
                }

                is SessionRefreshResult.TemporarilyUnavailable ->
                    AccessTokenState.TemporarilyUnavailable(refreshResult.cause)
            }
        }
    }

    fun save(session: AuthSession) {
        local.saveSession(session)
    }

    fun clear() {
        local.clear()
    }
}
