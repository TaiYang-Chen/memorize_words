package com.chen.memorizewords.core.network

import okhttp3.Interceptor
import okhttp3.Response

class BearerAuthInterceptor(
    private val accessTokenSource: AccessTokenSource,
    private val routePolicy: CoreNetworkRoutePolicy = CoreNetworkRoutePolicy()
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        if (!routePolicy.requiresAuthorization(originalRequest.url.encodedPath)) {
            return chain.proceed(originalRequest)
        }

        val requestWithToken = accessTokenSource.currentAccessToken()
            ?.takeIf(String::isNotBlank)
            ?.let { accessToken ->
                originalRequest.newBuilder()
                    .addHeader("Authorization", "Bearer $accessToken")
                    .build()
            }
            ?: originalRequest

        return chain.proceed(requestWithToken)
    }
}

class JsonAcceptHeaderInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val builder = original.newBuilder()
        if (original.header("Accept") == null) {
            builder.header("Accept", "application/json")
        }
        return chain.proceed(builder.build())
    }
}

class ClientCacheInterceptor(
    private val routePolicy: CoreNetworkRoutePolicy = CoreNetworkRoutePolicy()
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)
        val cacheControl = response.header("Cache-Control")
        return if (
            response.isSuccessful &&
            cacheControl.isNullOrEmpty() &&
            routePolicy.shouldApplyClientCache(
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
