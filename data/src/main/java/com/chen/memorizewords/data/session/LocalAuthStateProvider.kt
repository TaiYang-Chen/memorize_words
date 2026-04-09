package com.chen.memorizewords.data.session

import com.chen.memorizewords.data.local.mmkv.auth.AuthLocalDataSource
import com.chen.memorizewords.domain.auth.AuthStateProvider
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

@Singleton
class LocalAuthStateProvider @Inject constructor(
    private val sessionLocalDataSource: SessionLocalDataSource,
    private val authLocalDataSource: AuthLocalDataSource
) : AuthStateProvider {

    override fun isAuthenticated(): Boolean {
        return sessionLocalDataSource.getSession() != null &&
            authLocalDataSource.getUserId() != null
    }

    override fun observeAuthenticated(): Flow<Boolean> {
        return combine(
            sessionLocalDataSource.observeSession(),
            authLocalDataSource.getUserFlow()
        ) { session, user ->
            session != null && user?.userId != null
        }.distinctUntilChanged()
    }
}
