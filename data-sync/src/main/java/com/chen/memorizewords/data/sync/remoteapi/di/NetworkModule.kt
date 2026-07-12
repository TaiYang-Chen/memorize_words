package com.chen.memorizewords.data.sync.remoteapi.di

import android.content.Context
import com.chen.memorizewords.core.network.AccessTokenSource
import com.chen.memorizewords.core.network.CoreNetworkConfig
import com.chen.memorizewords.core.network.CoreNetworkFactory
import com.chen.memorizewords.core.network.CoreNetworkRoutePolicy
import com.chen.memorizewords.domain.account.auth.TokenProvider
import com.chen.memorizewords.data.sync.remoteapi.GlobalConfig
import com.chen.memorizewords.core.network.http.ApiResponseAdapterFactory
import com.chen.memorizewords.data.sync.remoteapi.api.datasync.UserDataSyncApiService
import com.chen.memorizewords.data.sync.remoteapi.api.appupdate.AppUpdateApiService
import com.chen.memorizewords.data.sync.remoteapi.api.learningsync.LearningSyncApiService
import com.chen.memorizewords.data.sync.remoteapi.constants.NetworkConstants
import com.chen.memorizewords.data.sync.remoteapi.eventlistener.NetworkEventListener
import com.chen.memorizewords.data.sync.remoteapi.interceptor.LoggingInterceptor
import com.chen.memorizewords.data.sync.remoteapi.interceptor.InstallationIdInterceptor
import com.squareup.moshi.Moshi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Singleton
import okhttp3.Cache
import okhttp3.OkHttpClient
import retrofit2.Retrofit

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideCoreNetworkConfig(): CoreNetworkConfig {
        require(GlobalConfig.baseUrl.startsWith("http://", ignoreCase = true)) {
            "Network baseUrl must use HTTP."
        }
        return CoreNetworkConfig(
            baseUrl = GlobalConfig.baseUrl,
            // LoggingInterceptor is the single opt-in debug logging path; release builds disable it.
            enableBodyLogging = false
        )
    }

    @Provides
    @Singleton
    fun provideCoreNetworkRoutePolicy(): CoreNetworkRoutePolicy {
        return CoreNetworkRoutePolicy()
    }

    @Provides
    @Singleton
    fun provideAccessTokenSource(tokenProvider: TokenProvider): AccessTokenSource {
        return object : AccessTokenSource {
            override fun currentAccessToken(): String? = tokenProvider.getAccessTokenIfValid()
        }
    }

    @Provides
    @Singleton
    fun provideCache(@ApplicationContext context: Context): Cache {
        return Cache(
            File(context.cacheDir, NetworkConstants.CACHE_DIR_NAME),
            NetworkConstants.CACHE_SIZE
        )
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(
        loggingInterceptor: LoggingInterceptor,
        installationIdInterceptor: InstallationIdInterceptor,
        cache: Cache,
        eventListenerFactory: NetworkEventListener.Factory,
        config: CoreNetworkConfig,
        accessTokenSource: AccessTokenSource,
        routePolicy: CoreNetworkRoutePolicy
    ): OkHttpClient {
        return CoreNetworkFactory.createOkHttpClient(
            config = config.copy(
                timeoutSeconds = NetworkConstants.TIMEOUT_SECONDS,
                connectionPoolMaxIdle = NetworkConstants.CONNECTION_POOL_MAX_IDLE,
                connectionPoolKeepAliveMinutes = NetworkConstants.CONNECTION_POOL_KEEP_ALIVE
            ),
            accessTokenSource = accessTokenSource,
            cache = cache,
            routePolicy = routePolicy,
            applicationInterceptors = listOf(installationIdInterceptor, loggingInterceptor)
        ).newBuilder()
            .eventListenerFactory(eventListenerFactory)
            .build()
    }

    @Provides
    @Singleton
    fun provideMoshi(): Moshi {
        return CoreNetworkFactory.createMoshi(
            extraAdapters = listOf(ApiResponseAdapterFactory())
        )
    }

    @Provides
    @Singleton
    fun provideRetrofit(
        okHttpClient: OkHttpClient,
        moshi: Moshi,
        config: CoreNetworkConfig
    ): Retrofit {
        return CoreNetworkFactory.createRetrofit(
            config = config,
            okHttpClient = okHttpClient,
            moshi = moshi
        )
    }

    @Provides
    @Singleton
    fun provideUserDataSyncApiService(retrofit: Retrofit): UserDataSyncApiService {
        return retrofit.create(UserDataSyncApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideLearningSyncApiService(retrofit: Retrofit): LearningSyncApiService {
        return retrofit.create(LearningSyncApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideAppUpdateApiService(retrofit: Retrofit): AppUpdateApiService {
        return retrofit.create(AppUpdateApiService::class.java)
    }
}
