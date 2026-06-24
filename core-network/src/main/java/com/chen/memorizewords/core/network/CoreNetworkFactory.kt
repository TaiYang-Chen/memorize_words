package com.chen.memorizewords.core.network

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.util.concurrent.TimeUnit
import okhttp3.Cache
import okhttp3.ConnectionPool
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

object CoreNetworkFactory {
    fun createMoshi(
        extraAdapters: List<JsonAdapter.Factory> = emptyList()
    ): Moshi {
        return Moshi.Builder().apply {
            extraAdapters.forEach(::add)
            add(KotlinJsonAdapterFactory())
        }.build()
    }

    fun createOkHttpClient(
        config: CoreNetworkConfig,
        accessTokenSource: AccessTokenSource,
        cache: Cache? = null,
        routePolicy: CoreNetworkRoutePolicy = CoreNetworkRoutePolicy(),
        applicationInterceptors: List<Interceptor> = emptyList(),
        networkInterceptors: List<Interceptor> = emptyList()
    ): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(BearerAuthInterceptor(accessTokenSource, routePolicy))
            .addInterceptor(JsonAcceptHeaderInterceptor())
            .apply {
                if (config.enableBodyLogging) {
                    addInterceptor(
                        HttpLoggingInterceptor().apply {
                            redactHeader("Authorization")
                            redactHeader("Cookie")
                            redactHeader("Set-Cookie")
                            level = HttpLoggingInterceptor.Level.BODY
                        }
                    )
                }
                applicationInterceptors.forEach(::addInterceptor)
                addNetworkInterceptor(ClientCacheInterceptor(routePolicy))
                networkInterceptors.forEach(::addNetworkInterceptor)
                cache?.let(::cache)
            }
            .connectionPool(
                ConnectionPool(
                    config.connectionPoolMaxIdle,
                    config.connectionPoolKeepAliveMinutes,
                    TimeUnit.MINUTES
                )
            )
            .retryOnConnectionFailure(true)
            .connectTimeout(config.timeoutSeconds, config.timeoutUnit)
            .readTimeout(config.timeoutSeconds, config.timeoutUnit)
            .writeTimeout(config.timeoutSeconds, config.timeoutUnit)
            .build()
    }

    fun createRetrofit(
        config: CoreNetworkConfig,
        okHttpClient: OkHttpClient,
        moshi: Moshi
    ): Retrofit {
        return Retrofit.Builder()
            .client(okHttpClient)
            .baseUrl(config.baseUrl)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
    }
}
