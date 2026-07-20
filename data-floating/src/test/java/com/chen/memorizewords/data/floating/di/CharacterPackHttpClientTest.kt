package com.chen.memorizewords.data.floating.di

import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import okhttp3.Authenticator
import okhttp3.CookieJar

class CharacterPackHttpClientTest {
    @Test
    fun `character downloads use an isolated anonymous HTTPS client`() {
        val client = DataFloatingDatabaseModule.provideCharacterPackHttpClient()

        assertTrue(client.interceptors.isEmpty())
        assertTrue(client.networkInterceptors.isEmpty())
        assertEquals(Authenticator.NONE, client.authenticator)
        assertEquals(Authenticator.NONE, client.proxyAuthenticator)
        assertEquals(CookieJar.NO_COOKIES, client.cookieJar)
        assertTrue(client.followRedirects)
        assertFalse(client.followSslRedirects)
        assertEquals(TimeUnit.SECONDS.toMillis(30), client.connectTimeoutMillis.toLong())
        assertEquals(TimeUnit.SECONDS.toMillis(30), client.readTimeoutMillis.toLong())
        assertEquals(TimeUnit.SECONDS.toMillis(30), client.writeTimeoutMillis.toLong())
    }
}
