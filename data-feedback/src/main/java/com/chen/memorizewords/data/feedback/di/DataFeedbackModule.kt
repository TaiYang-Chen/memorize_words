package com.chen.memorizewords.data.feedback.di

import com.chen.memorizewords.data.feedback.remoteapi.api.feedback.FeedbackApiService
import com.chen.memorizewords.data.feedback.repository.FeedbackRepositoryImpl
import com.chen.memorizewords.domain.feedback.repository.FeedbackRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import retrofit2.Retrofit

@Module
@InstallIn(SingletonComponent::class)
abstract class DataFeedbackRepositoryModule {
    @Binds
    abstract fun bindFeedbackRepository(impl: FeedbackRepositoryImpl): FeedbackRepository
}

@Module
@InstallIn(SingletonComponent::class)
object DataFeedbackNetworkModule {
    @Provides
    @Singleton
    fun provideFeedbackApiService(retrofit: Retrofit): FeedbackApiService {
        return retrofit.create(FeedbackApiService::class.java)
    }
}
