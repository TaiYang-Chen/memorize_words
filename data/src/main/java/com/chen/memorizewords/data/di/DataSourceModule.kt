package com.chen.memorizewords.data.di

import com.chen.memorizewords.data.remote.datasync.RemoteUserSyncDataSource
import com.chen.memorizewords.data.remote.datasync.RemoteUserSyncDataSourceImpl
import com.chen.memorizewords.data.remote.learningsync.RemoteLearningSyncDataSource
import com.chen.memorizewords.data.remote.learningsync.RemoteLearningSyncDataSourceImpl
import com.chen.memorizewords.data.remote.practice.RemoteExamPracticeDataSource
import com.chen.memorizewords.data.remote.practice.RemoteExamPracticeDataSourceImpl
import com.chen.memorizewords.data.remote.wordbook.RemoteWordBookDataSource
import com.chen.memorizewords.data.remote.wordbook.RemoteWordBookDataSourceImpl
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
    abstract fun bindRemoteWordBookDataSource(impl: RemoteWordBookDataSourceImpl): RemoteWordBookDataSource

    @Singleton
    @Binds
    abstract fun bindRemoteUserSyncDataSource(impl: RemoteUserSyncDataSourceImpl): RemoteUserSyncDataSource

    @Singleton
    @Binds
    abstract fun bindRemoteLearningSyncDataSource(
        impl: RemoteLearningSyncDataSourceImpl
    ): RemoteLearningSyncDataSource

    @Singleton
    @Binds
    abstract fun bindRemoteExamPracticeDataSource(
        impl: RemoteExamPracticeDataSourceImpl
    ): RemoteExamPracticeDataSource
}
