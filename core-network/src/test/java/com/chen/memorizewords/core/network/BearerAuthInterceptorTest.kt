package com.chen.memorizewords.core.network

import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import okhttp3.Call
import okhttp3.Connection
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody

class BearerAuthInterceptorTest {

    @Test
    fun `skip authorization header prevents bearer token and is removed`() {
        val chain = RecordingChain(
            Request.Builder()
                .url("https://example.com/api/auth/login")
                .header(CoreNetworkHeaders.SKIP_AUTHORIZATION, "true")
                .build()
        )

        BearerAuthInterceptor(FixedAccessTokenSource("token")).intercept(chain)

        assertNull(chain.proceededRequest.header("Authorization"))
        assertNull(chain.proceededRequest.header(CoreNetworkHeaders.SKIP_AUTHORIZATION))
    }

    @Test
    fun `protected request receives bearer token`() {
        val chain = RecordingChain(
            Request.Builder()
                .url("https://example.com/api/me")
                .build()
        )

        BearerAuthInterceptor(FixedAccessTokenSource("token")).intercept(chain)

        assertEquals("Bearer token", chain.proceededRequest.header("Authorization"))
    }

    private class FixedAccessTokenSource(
        private val token: String
    ) : AccessTokenSource {
        override fun currentAccessToken(): String = token
    }

    private class RecordingChain(
        private val request: Request
    ) : Interceptor.Chain {
        lateinit var proceededRequest: Request
            private set

        override fun request(): Request = request

        override fun proceed(request: Request): Response {
            proceededRequest = request
            return Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body("".toResponseBody(null))
                .build()
        }

        override fun connection(): Connection? = null

        override fun call(): Call = OkHttpClient().newCall(request)

        override fun connectTimeoutMillis(): Int = 10_000

        override fun withConnectTimeout(timeout: Int, unit: TimeUnit): Interceptor.Chain = this

        override fun readTimeoutMillis(): Int = 10_000

        override fun withReadTimeout(timeout: Int, unit: TimeUnit): Interceptor.Chain = this

        override fun writeTimeoutMillis(): Int = 10_000

        override fun withWriteTimeout(timeout: Int, unit: TimeUnit): Interceptor.Chain = this
    }
}
