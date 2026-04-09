package com.chen.memorizewords.speech

import com.chen.memorizewords.network.util.NetworkResult
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class AliyunTokenSnapshot(
    val token: String,
    val expireAtEpochSeconds: Long
)

interface AliyunTokenRemoteDataSource {
    suspend fun fetchToken(): AliyunTokenSnapshot
}

@Singleton
class BackendAliyunTokenRemoteDataSource @Inject constructor(
    private val speechInfraRequest: com.chen.memorizewords.network.api.speech.SpeechInfraRequest
) : AliyunTokenRemoteDataSource {

    override suspend fun fetchToken(): AliyunTokenSnapshot {
        return when (val result = speechInfraRequest.getAliyunToken()) {
            is NetworkResult.Success -> {
                val token = result.data.token.trim()
                val expireAt = result.data.expireAt
                if (token.isBlank()) {
                    throw AliyunApiException("Aliyun token response is empty.")
                }
                AliyunTokenSnapshot(token = token, expireAtEpochSeconds = expireAt)
            }

            is NetworkResult.Failure.Unauthorized -> throw AliyunAuthException(
                message = result.message ?: "Authenticated session required for Aliyun token.",
                code = result.code.toString()
            )

            is NetworkResult.Failure.NetworkError -> throw AliyunNetworkException(
                message = result.throwable.message ?: "Aliyun token request failed.",
                cause = result.throwable
            )

            is NetworkResult.Failure.HttpError -> throw AliyunApiException(
                message = result.message ?: "Aliyun token request failed.",
                code = result.code.toString()
            )

            is NetworkResult.Failure.GenericError -> throw AliyunApiException(result.message)
        }
    }
}

@Singleton
class AliyunTokenProvider internal constructor(
    private val remoteDataSource: AliyunTokenRemoteDataSource,
    private val nowEpochSeconds: () -> Long
) {

    @Inject
    constructor(remoteDataSource: AliyunTokenRemoteDataSource) : this(
        remoteDataSource = remoteDataSource,
        nowEpochSeconds = { System.currentTimeMillis() / 1000L }
    )

    @Volatile
    private var cachedToken: String? = null

    @Volatile
    private var expireAtEpochSeconds: Long = 0L

    private val mutex = Mutex()

    suspend fun getValidToken(forceRefresh: Boolean = false): String {
        if (!forceRefresh) {
            validCachedTokenOrNull()?.let { return it }
        }
        return mutex.withLock {
            if (!forceRefresh) {
                validCachedTokenOrNull()?.let { return@withLock it }
            }
            val snapshot = remoteDataSource.fetchToken()
            val now = nowEpochSeconds()
            if (snapshot.expireAtEpochSeconds <= now) {
                clearCache()
                throw AliyunAuthException("Aliyun token is already expired when received.")
            }
            val safeExpireAt = minOf(
                snapshot.expireAtEpochSeconds,
                now + MAX_TOKEN_LIFETIME_SECONDS
            )
            cachedToken = snapshot.token
            expireAtEpochSeconds = safeExpireAt
            snapshot.token
        }
    }

    private fun validCachedTokenOrNull(): String? {
        val token = cachedToken ?: return null
        val now = nowEpochSeconds()
        val expireAt = expireAtEpochSeconds
        if (expireAt <= now) {
            clearCache()
            return null
        }
        val remaining = expireAt - now
        val refreshWindow = minOf(MAX_REFRESH_WINDOW_SECONDS, maxOf(MIN_REFRESH_WINDOW_SECONDS, remaining / 2))
        return if (remaining <= refreshWindow) {
            null
        } else {
            token
        }
    }

    private fun clearCache() {
        cachedToken = null
        expireAtEpochSeconds = 0L
    }

    private companion object {
        const val MAX_TOKEN_LIFETIME_SECONDS = 24 * 60 * 60L
        const val MAX_REFRESH_WINDOW_SECONDS = 300L
        const val MIN_REFRESH_WINDOW_SECONDS = 60L
    }
}
