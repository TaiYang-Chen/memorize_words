package com.chen.memorizewords.data.sync.remoteapi.interceptor

import com.chen.memorizewords.data.sync.remoteapi.GlobalConfig
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor

@Singleton
class LoggingInterceptor @Inject constructor() : Interceptor {

    private val delegate = HttpLoggingInterceptor().apply {
        redactHeader("Authorization")
        redactHeader("Cookie")
        redactHeader("Set-Cookie")
        level = if (GlobalConfig.enableBodyLogging) {
            HttpLoggingInterceptor.Level.BODY
        } else {
            HttpLoggingInterceptor.Level.NONE
        }
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        return delegate.intercept(chain)
    }
}
