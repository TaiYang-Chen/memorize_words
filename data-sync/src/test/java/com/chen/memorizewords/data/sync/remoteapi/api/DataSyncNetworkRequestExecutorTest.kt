package com.chen.memorizewords.data.sync.remoteapi.api

import com.chen.memorizewords.core.network.http.AuthenticatedRequestOrigin
import com.chen.memorizewords.core.network.http.NetworkResult
import com.chen.memorizewords.domain.account.auth.AccessTokenState
import com.chen.memorizewords.domain.account.auth.TokenProvider
import com.chen.memorizewords.domain.sync.service.AuthenticatedRequestSuccessReporter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking

class DataSyncNetworkRequestExecutorTest {

    @Test
    fun `user request success wakes sync but sync request success does not`() = runBlocking {
        var wakeCount = 0
        val executor = DataSyncNetworkRequestExecutor(
            tokenProvider = AvailableTokenProvider,
            authenticatedRequestSuccessReporter = object : AuthenticatedRequestSuccessReporter {
                override suspend fun onAuthenticatedRequestSucceeded() {
                    wakeCount++
                }
            }
        )

        executor.executeAuthenticated { NetworkResult.Success(Unit) }
        executor.executeAuthenticated(AuthenticatedRequestOrigin.SYNC) {
            NetworkResult.Success(Unit)
        }

        assertEquals(1, wakeCount)
    }

    private object AvailableTokenProvider : TokenProvider {
        override suspend fun resolveAccessTokenState(
            notifyKickoutOnInvalidSession: Boolean
        ): AccessTokenState = AccessTokenState.Available("token")

        override fun getAccessTokenIfValid(): String = "token"
    }
}
