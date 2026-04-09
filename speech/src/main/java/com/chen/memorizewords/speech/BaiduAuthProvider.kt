package com.chen.memorizewords.speech

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

@Singleton
class BaiduAuthProvider @Inject constructor(
    @BaiduHttpClient private val httpClient: OkHttpClient,
    private val runtimeConfig: SpeechRuntimeConfig
) {

    private val mutex = Mutex()

    @Volatile
    private var cachedToken: BaiduAccessToken? = null

    suspend fun accessToken(): String {
        return mutex.withLock {
            val now = System.currentTimeMillis()
            cachedToken?.takeIf { it.isValid(now) }?.token ?: fetchAccessToken(now).also {
                cachedToken = it
            }.token
        }
    }

    private suspend fun fetchAccessToken(nowMillis: Long): BaiduAccessToken = withContext(Dispatchers.IO) {
        if (runtimeConfig.baiduApiKey.isBlank() || runtimeConfig.baiduSecretKey.isBlank()) {
            throw BaiduAuthException("Baidu API key or secret key is missing.")
        }
        val request = Request.Builder()
            .url(BAIDU_OAUTH_URL)
            .post(
                FormBody.Builder()
                    .add("grant_type", "client_credentials")
                    .add("client_id", runtimeConfig.baiduApiKey)
                    .add("client_secret", runtimeConfig.baiduSecretKey)
                    .build()
            )
            .build()
        runCatching {
            httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                val json = JSONObject(body.ifBlank { "{}" })
                if (!response.isSuccessful) {
                    throw BaiduAuthException(
                        message = json.optString("error_description").ifBlank {
                            json.optString("error").ifBlank { "Baidu auth failed." }
                        },
                        code = response.code.toString()
                    )
                }
                val accessToken = json.optString("access_token")
                if (accessToken.isBlank()) {
                    throw BaiduAuthException(
                        message = json.optString("error_description").ifBlank {
                            json.optString("error").ifBlank {
                                "Baidu auth response does not contain access_token."
                            }
                        },
                        code = json.optString("error")
                    )
                }
                val expiresInSeconds = json.optLong("expires_in", 0L).coerceAtLeast(0L)
                BaiduAccessToken(
                    token = accessToken,
                    expiresAtMillis = nowMillis + (expiresInSeconds * 1000L) - TOKEN_EXPIRY_SAFETY_WINDOW_MS
                )
            }
        }.getOrElse { throw it.toBaiduClientException() }
    }

    private companion object {
        const val BAIDU_OAUTH_URL = "https://aip.baidubce.com/oauth/2.0/token"
        const val TOKEN_EXPIRY_SAFETY_WINDOW_MS = 60_000L
    }
}
