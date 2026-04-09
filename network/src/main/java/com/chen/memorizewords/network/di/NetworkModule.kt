package com.chen.memorizewords.network.di

import android.content.Context
import com.chen.memorizewords.domain.auth.TokenProvider
import com.chen.memorizewords.network.GlobalConfig
import com.chen.memorizewords.network.adapter.ApiResponseAdapterFactory
import com.chen.memorizewords.network.api.auth.AuthApiService
import com.chen.memorizewords.network.api.datasync.UserDataSyncApiService
import com.chen.memorizewords.network.api.feedback.FeedbackApiService
import com.chen.memorizewords.network.api.learningsync.LearningSyncApiService
import com.chen.memorizewords.network.api.practice.ExamPracticeApiService
import com.chen.memorizewords.network.api.practice.PracticeApiService
import com.chen.memorizewords.network.api.speech.SpeechInfraApiService
import com.chen.memorizewords.network.api.wordbook.WordBookApiService
import com.chen.memorizewords.network.constants.NetworkConstants
import com.chen.memorizewords.network.eventlistener.NetworkEventListener
import com.chen.memorizewords.network.interceptor.AuthInterceptor
import com.chen.memorizewords.network.interceptor.HeaderInterceptor
import com.chen.memorizewords.network.interceptor.LoggingInterceptor
import com.chen.memorizewords.network.interceptor.OnlineCacheInterceptor
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import okhttp3.Cache
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideAuthInterceptor(tokenProvider: TokenProvider): AuthInterceptor {
        return AuthInterceptor(tokenProvider)
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
        headerInterceptor: HeaderInterceptor,
        authInterceptor: AuthInterceptor,
        onlineCacheInterceptor: OnlineCacheInterceptor,
        cache: Cache,
        eventListenerFactory: NetworkEventListener.Factory
    ): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(headerInterceptor)
            .addInterceptor(loggingInterceptor)
            .addNetworkInterceptor(onlineCacheInterceptor)
            .cache(cache)
            .eventListenerFactory(eventListenerFactory)
            .connectionPool(
                ConnectionPool(
                    NetworkConstants.CONNECTION_POOL_MAX_IDLE,
                    NetworkConstants.CONNECTION_POOL_KEEP_ALIVE,
                    TimeUnit.MINUTES
                )
            )
            .retryOnConnectionFailure(true)
            .connectTimeout(NetworkConstants.TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(NetworkConstants.TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(NetworkConstants.TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideMoshi(): Moshi {
        return Moshi.Builder()
            .add(ApiResponseAdapterFactory())
            .add(KotlinJsonAdapterFactory())
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient, moshi: Moshi): Retrofit {
        return Retrofit.Builder()
            .client(okHttpClient)
            .baseUrl(GlobalConfig.baseUrl)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
    }

    @Provides
    @Singleton
    fun provideAuthApiService(retrofit: Retrofit): AuthApiService {
        return retrofit.create(AuthApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideWordBookApiService(retrofit: Retrofit): WordBookApiService {
        return retrofit.create(WordBookApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideUserDataSyncApiService(retrofit: Retrofit): UserDataSyncApiService {
        return retrofit.create(UserDataSyncApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideFeedbackApiService(retrofit: Retrofit): FeedbackApiService {
        return retrofit.create(FeedbackApiService::class.java)
    }

    @Provides
    @Singleton
    fun providePracticeApiService(retrofit: Retrofit): PracticeApiService {
        return retrofit.create(PracticeApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideExamPracticeApiService(retrofit: Retrofit): ExamPracticeApiService {
        return retrofit.create(ExamPracticeApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideSpeechInfraApiService(retrofit: Retrofit): SpeechInfraApiService {
        return retrofit.create(SpeechInfraApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideLearningSyncApiService(retrofit: Retrofit): LearningSyncApiService {
        return retrofit.create(LearningSyncApiService::class.java)
    }
}
