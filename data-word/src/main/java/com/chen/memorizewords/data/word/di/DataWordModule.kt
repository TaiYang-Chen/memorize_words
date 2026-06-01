package com.chen.memorizewords.data.word.di

import android.content.Context
import com.chen.memorizewords.core.database.DestructiveRoomDatabaseFactory
import com.chen.memorizewords.core.database.NewArchitectureDatabase
import com.chen.memorizewords.data.word.local.WordDatabase
import com.chen.memorizewords.data.word.remote.wordbook.RemoteWordBookDataSource
import com.chen.memorizewords.data.word.remote.wordbook.RemoteWordBookDataSourceImpl
import com.chen.memorizewords.data.word.remoteapi.api.wordbook.WordBookApiService
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import retrofit2.Retrofit

@Module
@InstallIn(SingletonComponent::class)
abstract class DataWordModule {
    @Binds
    abstract fun bindRemoteWordBookDataSource(
        impl: RemoteWordBookDataSourceImpl
    ): RemoteWordBookDataSource
}

@Module
@InstallIn(SingletonComponent::class)
object DataWordDatabaseModule {
    @Provides
    @Singleton
    fun provideWordDatabase(@ApplicationContext context: Context): WordDatabase {
        return DestructiveRoomDatabaseFactory(
            databaseName = NewArchitectureDatabase.contextName("word")
        ).build(context, WordDatabase::class.java)
    }

    @Provides
    fun provideWordDao(database: WordDatabase) = database.wordDao()

    @Provides
    fun provideWordDefinitionDao(database: WordDatabase) = database.wordDefinitionDao()

    @Provides
    fun provideWordExampleDao(database: WordDatabase) = database.wordExampleDao()

    @Provides
    fun provideWordFormDao(database: WordDatabase) = database.wordFormDao()

    @Provides
    fun provideWordUserMetaDao(database: WordDatabase) = database.wordUserMetaDao()

    @Provides
    fun provideWordRelationDao(database: WordDatabase) = database.wordRelationDao()

    @Provides
    fun provideWordRootDao(database: WordDatabase) = database.wordRootDao()

    @Provides
    fun provideRootTagDao(database: WordDatabase) = database.rootTagDao()

    @Provides
    fun provideRootWordDao(database: WordDatabase) = database.rootWordDao()

    @Provides
    @Singleton
    fun provideWordBookApiService(retrofit: Retrofit): WordBookApiService {
        return retrofit.create(WordBookApiService::class.java)
    }
}
