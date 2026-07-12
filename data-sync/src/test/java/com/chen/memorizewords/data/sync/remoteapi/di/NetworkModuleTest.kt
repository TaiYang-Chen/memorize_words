package com.chen.memorizewords.data.sync.remoteapi.di

import com.chen.memorizewords.data.sync.remoteapi.constants.NetworkConstants
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import okhttp3.OkHttpClient

class NetworkModuleTest {

    @Test
    fun `download client keeps relaxed timeouts`() {
        val apiClient = OkHttpClient.Builder()
            .connectTimeout(NetworkConstants.API_CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(NetworkConstants.API_READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(NetworkConstants.API_WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()

        val downloadClient = NetworkModule.provideDownloadOkHttpClient(apiClient)

        assertEquals(30_000, downloadClient.connectTimeoutMillis)
        assertEquals(30_000, downloadClient.readTimeoutMillis)
        assertEquals(30_000, downloadClient.writeTimeoutMillis)
        assertEquals(apiClient.connectionPool, downloadClient.connectionPool)
        assertEquals(apiClient.dispatcher, downloadClient.dispatcher)
    }
}
