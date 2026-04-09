package com.chen.memorizewords.data.session

import com.chen.memorizewords.data.remote.mapFailureToException
import com.chen.memorizewords.network.api.auth.AuthRequest
import com.chen.memorizewords.network.util.NetworkResult
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthSessionRefreshRemoteDataSource @Inject constructor(
    private val authRequest: AuthRequest
) : SessionRefreshDataSource {

    override suspend fun refreshToken(refreshToken: String): SessionRefreshResult {
        return when (val result = authRequest.refresh(refreshToken)) {
            is NetworkResult.Success -> {
                val loginDto = result.data
                SessionRefreshResult.Refreshed(
                    AuthSession(
                        accessToken = loginDto.token,
                        refreshToken = loginDto.refreshToken,
                        expiresAt = System.currentTimeMillis() + loginDto.expiresIn * 1000L
                    )
                )
            }

            is NetworkResult.Failure.HttpError -> {
                if (result.code == 401) {
                    SessionRefreshResult.InvalidSession
                } else {
                    SessionRefreshResult.TemporarilyUnavailable(mapFailureToException(result))
                }
            }

            is NetworkResult.Failure.Unauthorized -> SessionRefreshResult.InvalidSession

            is NetworkResult.Failure.NetworkError ->
                SessionRefreshResult.TemporarilyUnavailable(mapFailureToException(result))

            is NetworkResult.Failure.GenericError ->
                SessionRefreshResult.TemporarilyUnavailable(mapFailureToException(result))
        }
    }
}

sealed interface SessionRefreshResult {
    data class Refreshed(val session: AuthSession) : SessionRefreshResult
    data object InvalidSession : SessionRefreshResult
    data class TemporarilyUnavailable(val cause: Throwable) : SessionRefreshResult
}
