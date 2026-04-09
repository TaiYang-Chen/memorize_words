package com.chen.memorizewords.di

import com.chen.memorizewords.core.common.resource.ResourceProvider
import com.chen.memorizewords.resource.AndroidResourceProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ResourceProviderModule {

    @Binds
    @Singleton
    abstract fun bindResourceProvider(
        impl: AndroidResourceProvider
    ): ResourceProvider
}
