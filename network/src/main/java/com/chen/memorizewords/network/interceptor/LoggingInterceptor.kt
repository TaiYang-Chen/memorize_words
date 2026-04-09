package com.chen.memorizewords.network.interceptor

import com.chen.memorizewords.network.GlobalConfig
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor

@Singleton
class LoggingInterceptor @Inject constructor() : Interceptor {

    private val delegate = HttpLoggingInterceptor().apply {
        level = if (GlobalConfig.isDebug) {
            HttpLoggingInterceptor.Level.BODY
        } else {
            HttpLoggingInterceptor.Level.NONE
        }
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        return delegate.intercept(chain)
    }
}
