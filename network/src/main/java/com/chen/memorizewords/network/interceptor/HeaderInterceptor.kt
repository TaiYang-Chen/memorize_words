package com.chen.memorizewords.network.interceptor

import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HeaderInterceptor @Inject constructor() : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val builder = original.newBuilder()
        // Let OkHttp/Retrofit derive Content-Type from the request body.
        // For multipart requests this preserves the generated boundary.
        if (original.header("Accept") == null) {
            builder.header("Accept", "application/json")
        }

        return chain.proceed(builder.build())
    }
}
