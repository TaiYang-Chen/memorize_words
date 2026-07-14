package com.chen.memorizewords.data.sync.di

import android.content.Context
import com.chen.memorizewords.core.database.DestructiveRoomDatabaseFactory
import com.chen.memorizewords.core.database.NewArchitectureDatabase
import com.chen.memorizewords.data.sync.local.room.SyncDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideSyncDatabase(@ApplicationContext context: Context): SyncDatabase {
        return DestructiveRoomDatabaseFactory(
            databaseName = NewArchitectureDatabase.contextName("sync_outbox")
        ).build(context, SyncDatabase::class.java) {
            enableMultiInstanceInvalidation()
        }
    }

    @Provides
    fun provideSyncOutboxDao(syncDatabase: SyncDatabase) = syncDatabase.syncOutboxDao()

    @Provides
    fun provideFailedSyncEventDao(syncDatabase: SyncDatabase) = syncDatabase.failedSyncEventDao()
}
