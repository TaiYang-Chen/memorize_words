package com.chen.memorizewords.data.di

import com.chen.memorizewords.domain.practice.DefaultPracticeWordProvider
import com.chen.memorizewords.domain.practice.PracticeWordProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class PracticeApplicationModule {

    @Binds
    @Singleton
    abstract fun bindPracticeWordProvider(
        impl: DefaultPracticeWordProvider
    ): PracticeWordProvider
}
