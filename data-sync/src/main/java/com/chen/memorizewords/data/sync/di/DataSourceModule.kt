package com.chen.memorizewords.data.sync.di

import com.chen.memorizewords.data.sync.remote.datasync.RemoteUserSyncDataSource
import com.chen.memorizewords.data.sync.remote.datasync.RemoteUserSyncDataSourceImpl
import com.chen.memorizewords.data.sync.repository.sync.StudySyncPortImpl
import com.chen.memorizewords.data.sync.repository.sync.LearningEventSyncPortImpl
import com.chen.memorizewords.domain.study.repository.sync.LearningEventSyncPort
import com.chen.memorizewords.domain.study.repository.sync.StudySyncPort
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

    @Binds
    abstract fun bindStudySyncPort(impl: StudySyncPortImpl): StudySyncPort

    @Binds
    abstract fun bindLearningEventSyncPort(impl: LearningEventSyncPortImpl): LearningEventSyncPort

    @Singleton
    @Binds
    abstract fun bindRemoteLearningSyncDataSource(
        impl: RemoteLearningSyncDataSourceImpl
    ): RemoteLearningSyncDataSource
}
