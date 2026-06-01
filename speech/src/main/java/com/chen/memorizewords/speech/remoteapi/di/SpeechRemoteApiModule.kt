package com.chen.memorizewords.speech.remoteapi.di

import com.chen.memorizewords.speech.remoteapi.api.practice.PracticeApiService
import com.chen.memorizewords.speech.remoteapi.api.speech.SpeechInfraApiService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import retrofit2.Retrofit

@Module
@InstallIn(SingletonComponent::class)
object SpeechRemoteApiModule {
    @Provides
    @Singleton
    fun provideSpeechInfraApiService(retrofit: Retrofit): SpeechInfraApiService {
        return retrofit.create(SpeechInfraApiService::class.java)
    }

    @Provides
    @Singleton
    fun providePracticeApiService(retrofit: Retrofit): PracticeApiService {
        return retrofit.create(PracticeApiService::class.java)
    }
}
