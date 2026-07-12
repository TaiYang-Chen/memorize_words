package com.chen.memorizewords.core.network

import kotlin.test.Test
import kotlin.test.assertEquals

class CoreNetworkFactoryTest {

    @Test
    fun `default client uses short api timeouts`() {
        val client = CoreNetworkFactory.createOkHttpClient(
            config = CoreNetworkConfig(baseUrl = "http://localhost/"),
            accessTokenSource = object : AccessTokenSource {
                override fun currentAccessToken(): String? = null
            }
        )

        assertEquals(5_000, client.connectTimeoutMillis)
        assertEquals(15_000, client.readTimeoutMillis)
        assertEquals(15_000, client.writeTimeoutMillis)
    }

    @Test
    fun `client applies independently configured timeouts`() {
        val client = CoreNetworkFactory.createOkHttpClient(
            config = CoreNetworkConfig(
                baseUrl = "http://localhost/",
                connectTimeoutSeconds = 2,
                readTimeoutSeconds = 7,
                writeTimeoutSeconds = 9
            ),
            accessTokenSource = object : AccessTokenSource {
                override fun currentAccessToken(): String? = null
            }
        )

        assertEquals(2_000, client.connectTimeoutMillis)
        assertEquals(7_000, client.readTimeoutMillis)
        assertEquals(9_000, client.writeTimeoutMillis)
    }
}
