package com.chen.memorizewords.data.floating.di

import android.content.Context
import com.chen.memorizewords.core.database.DestructiveRoomDatabaseFactory
import com.chen.memorizewords.core.database.NewArchitectureDatabase
import com.chen.memorizewords.data.floating.repository.bootstrap.FloatingSnapshotLocalStateStore
import com.chen.memorizewords.data.floating.local.FloatingDatabase
import com.chen.memorizewords.data.floating.repository.FloatingWordDisplayRecordRepositoryImpl
import com.chen.memorizewords.data.floating.repository.FloatingWordSettingsRepositoryImpl
import com.chen.memorizewords.domain.floating.FloatingSnapshotLocalStatePort
import com.chen.memorizewords.domain.floating.FloatingSettingsLocalStatePort
import com.chen.memorizewords.domain.floating.repository.FloatingWordDisplayRecordRepository
import com.chen.memorizewords.domain.floating.repository.FloatingWordSettingsRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DataFloatingModule {
    @Binds
    abstract fun bindFloatingWordSettingsRepository(
        impl: FloatingWordSettingsRepositoryImpl
    ): FloatingWordSettingsRepository

    @Binds
    abstract fun bindFloatingSettingsLocalStatePort(
        impl: FloatingWordSettingsRepositoryImpl
    ): FloatingSettingsLocalStatePort

    @Binds
    abstract fun bindFloatingWordDisplayRecordRepository(
        impl: FloatingWordDisplayRecordRepositoryImpl
    ): FloatingWordDisplayRecordRepository

    @Binds
    abstract fun bindFloatingSnapshotLocalStatePort(
        impl: FloatingSnapshotLocalStateStore
    ): FloatingSnapshotLocalStatePort
}

@Module
@InstallIn(SingletonComponent::class)
object DataFloatingDatabaseModule {
    @Provides
    @Singleton
    fun provideFloatingDatabase(@ApplicationContext context: Context): FloatingDatabase {
        return DestructiveRoomDatabaseFactory(
            databaseName = NewArchitectureDatabase.contextName("floating")
        ).build(context, FloatingDatabase::class.java)
    }

    @Provides
    fun provideFloatingWordDisplayRecordDao(database: FloatingDatabase) =
        database.floatingWordDisplayRecordDao()
}
