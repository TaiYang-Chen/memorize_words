package com.chen.memorizewords.data.repository.sync

import com.chen.memorizewords.data.local.room.AppDatabase
import com.chen.memorizewords.data.remote.learningsync.RemoteLearningSyncDataSource
import com.chen.memorizewords.domain.auth.AuthStateProvider
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface LearningSyncWorkerEntryPoint {
    fun remoteLearningSyncDataSource(): RemoteLearningSyncDataSource
    fun authStateProvider(): AuthStateProvider
    fun appDatabase(): AppDatabase
}
