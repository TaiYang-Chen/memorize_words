package com.chen.memorizewords.network.interceptor

import com.chen.memorizewords.domain.auth.TokenProvider
import com.chen.memorizewords.network.policy.NetworkRoutePolicy
import okhttp3.Interceptor
import okhttp3.Response

class AuthInterceptor(
    private val tokenProvider: TokenProvider
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        if (!NetworkRoutePolicy.requiresAuthorization(originalRequest.url.encodedPath)) {
            return chain.proceed(originalRequest)
        }

        val requestWithToken = tokenProvider.getAccessTokenIfValid()
            ?.let { accessToken ->
                originalRequest.newBuilder()
                    .addHeader("Authorization", "Bearer $accessToken")
                    .build()
            }
            ?: originalRequest

        return chain.proceed(requestWithToken)
    }
}
