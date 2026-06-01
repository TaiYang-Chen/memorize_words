package com.chen.memorizewords.data.sync.di

import com.chen.memorizewords.data.sync.remote.datasync.RemoteUserSyncDataSource
import com.chen.memorizewords.data.sync.remote.datasync.RemoteUserSyncDataSourceImpl
import com.chen.memorizewords.data.sync.remote.learningsync.RemoteLearningSyncDataSource
import com.chen.memorizewords.data.sync.remote.learningsync.RemoteLearningSyncDataSourceImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DataSourceModule {

    @Singleton
    @Binds
    abstract fun bindRemoteUserSyncDataSource(impl: RemoteUserSyncDataSourceImpl): RemoteUserSyncDataSource

    @Singleton
    @Binds
    abstract fun bindRemoteLearningSyncDataSource(
        impl: RemoteLearningSyncDataSourceImpl
    ): RemoteLearningSyncDataSource
}
