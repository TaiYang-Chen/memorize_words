package com.chen.memorizewords.network.interceptor

import com.chen.memorizewords.network.policy.NetworkRoutePolicy
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OnlineCacheInterceptor @Inject constructor() : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)
        val cacheControl = response.header("Cache-Control")
        return if (
            response.isSuccessful &&
            cacheControl.isNullOrEmpty() &&
            NetworkRoutePolicy.shouldApplyClientCache(
                method = request.method,
                encodedPath = request.url.encodedPath
            )
        ) {
            response.newBuilder()
                .header("Cache-Control", "public, max-age=60")
                .build()
        } else {
            response
        }
    }
}

